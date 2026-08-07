package com.tikitaka.bidwinback.global.storage;

import com.tikitaka.bidwinback.global.config.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.util.HashSet;
import java.util.List;
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
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(properties.presignDuration())
                .putObjectRequest(putObjectRequest)
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
