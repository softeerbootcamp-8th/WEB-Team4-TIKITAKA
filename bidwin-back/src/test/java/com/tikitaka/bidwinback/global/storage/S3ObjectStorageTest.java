package com.tikitaka.bidwinback.global.storage;

import com.tikitaka.bidwinback.global.config.S3Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class S3ObjectStorageTest {

    private static final String BUCKET = "bidwin-image-bucket";
    private static final Duration PRESIGN_DURATION = Duration.ofMinutes(5);
    private static final S3Properties PROPERTIES = new S3Properties(
            BUCKET,
            "ap-northeast-2",
            PRESIGN_DURATION
    );

    @Test
    void 업로드_조건으로_Presigned_PUT_URL을_생성한다() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        S3ObjectStorage storage = new S3ObjectStorage(
                s3Client,
                presigner,
                PROPERTIES
        );
        PresignedPutObjectRequest presignedRequest =
                mock(PresignedPutObjectRequest.class);
        String objectKey = "auction-images/image-id.jpg";
        String contentType = "image/jpeg";
        long contentLength = 248_392L;
        String url = "https://example.com/presigned-upload";
        Map<String, List<String>> signedHeaders = Map.of(
                "Content-Type",
                List.of(contentType)
        );
        Instant expiresAt = Instant.parse("2026-07-28T06:10:00Z");

        when(presignedRequest.url()).thenReturn(URI.create(url).toURL());
        when(presignedRequest.signedHeaders()).thenReturn(signedHeaders);
        when(presignedRequest.expiration()).thenReturn(expiresAt);
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presignedRequest);

        PresignedUpload result = storage.presignPut(
                objectKey,
                contentType,
                contentLength
        );

        ArgumentCaptor<PutObjectPresignRequest> captor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(presigner).presignPutObject(captor.capture());
        PutObjectPresignRequest request = captor.getValue();
        PutObjectRequest putObjectRequest = request.putObjectRequest();
        assertAll(
                () -> assertThat(request.signatureDuration())
                        .isEqualTo(PRESIGN_DURATION),
                () -> assertThat(putObjectRequest.bucket()).isEqualTo(BUCKET),
                () -> assertThat(putObjectRequest.key()).isEqualTo(objectKey),
                () -> assertThat(putObjectRequest.contentType())
                        .isEqualTo(contentType),
                () -> assertThat(putObjectRequest.contentLength())
                        .isEqualTo(contentLength),
                () -> assertThat(result.url()).isEqualTo(url),
                () -> assertThat(result.signedHeaders()).isEqualTo(signedHeaders),
                () -> assertThat(result.expiresAt()).isEqualTo(expiresAt)
        );
        verifyNoInteractions(s3Client);
    }

    @Test
    void 객체별_삭제_성공과_실패를_구분한다() {
        S3Client s3Client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        S3ObjectStorage storage = new S3ObjectStorage(
                s3Client,
                presigner,
                PROPERTIES
        );
        List<String> objectKeys = List.of(
                "auction-images/first.jpg",
                "auction-images/second.jpg"
        );
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(DeleteObjectsResponse.builder()
                        .errors(S3Error.builder()
                                .key("auction-images/second.jpg")
                                .code("InternalError")
                                .build())
                        .build());

        ObjectDeletionResult result = storage.deleteAll(objectKeys);

        ArgumentCaptor<DeleteObjectsRequest> captor =
                ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(captor.capture());
        DeleteObjectsRequest request = captor.getValue();
        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.delete().objects())
                .extracting(object -> object.key())
                .containsExactlyElementsOf(objectKeys);
        assertThat(result.deletedKeys())
                .containsExactly("auction-images/first.jpg");
        assertThat(result.failures())
                .containsExactly(new ObjectDeletionResult.Failure(
                        "auction-images/second.jpg",
                        "InternalError"
                ));
        verifyNoInteractions(presigner);
    }

    @Test
    void 삭제할_객체가_없으면_S3를_호출하지_않는다() {
        S3Client s3Client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        S3ObjectStorage storage = new S3ObjectStorage(
                s3Client,
                presigner,
                PROPERTIES
        );

        ObjectDeletionResult result = storage.deleteAll(List.of());

        assertThat(result.deletedKeys()).isEmpty();
        assertThat(result.failures()).isEmpty();
        verifyNoInteractions(s3Client, presigner);
    }
}
