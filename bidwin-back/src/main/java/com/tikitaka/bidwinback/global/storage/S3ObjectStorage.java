package com.tikitaka.bidwinback.global.storage;

import com.tikitaka.bidwinback.global.config.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    @Override
    public PresignedUpload presignPut(
            String objectKey,
            String contentType,
            long contentLength
    ) {
        return presignPut(objectKey, contentType, contentLength, null);
    }

    @Override
    public PresignedUpload presignPut(
            String objectKey,
            String contentType,
            long contentLength,
            String checksumSha256
    ) {
        PutObjectRequest.Builder putObjectRequestBuilder = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength);
        if (checksumSha256 != null) {
            // 체크섬을 서명 조건에 포함해 다른 파일 내용으로 PUT하는 요청을 S3가 거부하게 한다.
            putObjectRequestBuilder.checksumSHA256(checksumSha256);
        }

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(properties.presignDuration())
                .putObjectRequest(putObjectRequestBuilder.build())
                .build();

        PresignedPutObjectRequest presignedRequest =
                s3Presigner.presignPutObject(presignRequest);

        return new PresignedUpload(
                presignedRequest.url().toString(),
                presignedRequest.signedHeaders(),
                presignedRequest.expiration()
        );
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw exception;
        }
    }

    @Override
    public Optional<StoredObjectMetadata> head(String objectKey) {
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    // 저장된 checksumSHA256을 HEAD 응답으로 받으려면 ChecksumMode를 명시해야 한다.
                    .checksumMode(ChecksumMode.ENABLED)
                    .build());
            return Optional.of(new StoredObjectMetadata(
                    response.contentLength(),
                    response.contentType(),
                    response.checksumSHA256()
            ));
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    @Override
    public void copy(String sourceObjectKey, String destinationObjectKey) {
        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(properties.bucket())
                .sourceKey(sourceObjectKey)
                .destinationBucket(properties.bucket())
                .destinationKey(destinationObjectKey)
                .build());
    }

    @Override
    public ObjectDeletionResult deleteAll(List<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return new ObjectDeletionResult(List.of(), List.of());
        }

        DeleteObjectsResponse response = s3Client.deleteObjects(
                DeleteObjectsRequest.builder()
                        .bucket(properties.bucket())
                        .delete(Delete.builder()
                                .objects(objectKeys.stream()
                                        .map(key -> ObjectIdentifier.builder()
                                                .key(key)
                                                .build())
                                        .toList())
                                .build())
                        .build()
        );

        List<ObjectDeletionResult.Failure> failures = response.errors().stream()
                .map(error -> new ObjectDeletionResult.Failure(
                        error.key(),
                        error.code()
                ))
                .toList();

        Set<String> failedKeys = new HashSet<>();
        failures.forEach(failure -> failedKeys.add(failure.objectKey()));
        List<String> deletedKeys = objectKeys.stream()
                .filter(key -> !failedKeys.contains(key))
                .toList();

        return new ObjectDeletionResult(deletedKeys, failures);
    }
}
