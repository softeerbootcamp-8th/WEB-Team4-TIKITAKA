package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.global.config.S3Properties;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.upload.domain.AuctionImageFileType;
import com.tikitaka.bidwinback.upload.domain.PendingAuctionImageStore;
import com.tikitaka.bidwinback.upload.domain.UploadException;
import com.tikitaka.bidwinback.upload.presentation.dto.AuctionImagePresignRequest;
import com.tikitaka.bidwinback.upload.presentation.dto.AuctionImagePresignResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuctionImagePresignServiceTest {

    private static final String BUCKET = "bidwin-image-bucket";
    private static final Duration PRESIGN_DURATION = Duration.ofMinutes(5);

    @Test
    void 이미지_업로드용_Presigned_URL과_서명된_헤더를_발급한다() throws Exception {
        S3Presigner s3Presigner = mock(S3Presigner.class);
        AuctionImageObjectKeyGenerator objectKeyGenerator =
                mock(AuctionImageObjectKeyGenerator.class);
        PendingAuctionImageStore pendingAuctionImageStore =
                mock(PendingAuctionImageStore.class);
        S3Properties properties = new S3Properties(
                BUCKET,
                "ap-northeast-2",
                PRESIGN_DURATION
        );
        AuctionImagePresignService service = new AuctionImagePresignService(
                s3Presigner,
                properties,
                objectKeyGenerator,
                pendingAuctionImageStore
        );
        long memberId = 1L;
        UUID draftId = UUID.fromString("44eac1aa-827d-40c3-b3e9-c44abb94ed09");
        AuctionImagePresignRequest request = new AuctionImagePresignRequest(
                "headphone.jpeg",
                "image/jpeg",
                248_392L
        );
        String objectKey = "auction-images/image-id.jpg";
        URL presignedUrl = URI.create("https://example.com/presigned-upload").toURL();
        Instant expiresAt = Instant.parse("2026-07-28T06:10:00Z");
        Map<String, List<String>> signedHeaders = Map.of(
                "Content-Type",
                List.of("image/jpeg")
        );
        PresignedPutObjectRequest presignedRequest =
                mock(PresignedPutObjectRequest.class);

        when(objectKeyGenerator.generate(AuctionImageFileType.JPEG))
                .thenReturn(objectKey);
        when(presignedRequest.url()).thenReturn(presignedUrl);
        when(presignedRequest.signedHeaders()).thenReturn(signedHeaders);
        when(presignedRequest.expiration()).thenReturn(expiresAt);
        when(s3Presigner.presignPutObject(
                org.mockito.ArgumentMatchers.any(PutObjectPresignRequest.class)
        )).thenReturn(presignedRequest);

        AuctionImagePresignResponse response = service.issue(
                memberId,
                draftId,
                List.of(request)
        ).getFirst();

        ArgumentCaptor<PutObjectPresignRequest> captor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());
        PutObjectPresignRequest capturedPresignRequest = captor.getValue();
        PutObjectRequest capturedPutObjectRequest =
                capturedPresignRequest.putObjectRequest();

        assertAll(
                () -> assertEquals(
                        presignedUrl.toString(),
                        response.presignedUrl()
                ),
                () -> assertEquals(objectKey, response.objectKey()),
                () -> assertEquals(signedHeaders, response.signedHeaders()),
                () -> assertEquals(expiresAt, response.expiresAt()),
                () -> assertEquals(
                        PRESIGN_DURATION,
                        capturedPresignRequest.signatureDuration()
                ),
                () -> assertEquals(BUCKET, capturedPutObjectRequest.bucket()),
                () -> assertEquals(objectKey, capturedPutObjectRequest.key()),
                () -> assertEquals(
                        "image/jpeg",
                        capturedPutObjectRequest.contentType()
                ),
                () -> assertEquals(
                        capturedPutObjectRequest.contentType(),
                        response.signedHeaders().get("Content-Type").getFirst()
                ),
                () -> assertEquals(
                        request.size(),
                        capturedPutObjectRequest.contentLength()
                )
        );
        verify(objectKeyGenerator).generate(AuctionImageFileType.JPEG);
        verify(pendingAuctionImageStore).saveAll(
                memberId,
                draftId,
                List.of(objectKey)
        );
    }

    @Test
    void 여러_이미지의_Presigned_URL을_요청_순서대로_발급한다() throws Exception {
        S3Presigner s3Presigner = mock(S3Presigner.class);
        AuctionImageObjectKeyGenerator objectKeyGenerator =
                mock(AuctionImageObjectKeyGenerator.class);
        PendingAuctionImageStore pendingAuctionImageStore =
                mock(PendingAuctionImageStore.class);
        S3Properties properties = new S3Properties(
                BUCKET,
                "ap-northeast-2",
                PRESIGN_DURATION
        );
        AuctionImagePresignService service = new AuctionImagePresignService(
                s3Presigner,
                properties,
                objectKeyGenerator,
                pendingAuctionImageStore
        );
        long memberId = 1L;
        UUID draftId = UUID.fromString("44eac1aa-827d-40c3-b3e9-c44abb94ed09");
        AuctionImagePresignRequest firstRequest = new AuctionImagePresignRequest(
                "headphone.jpg",
                "image/jpeg",
                248_392L
        );
        AuctionImagePresignRequest secondRequest = new AuctionImagePresignRequest(
                "keyboard.png",
                "image/png",
                128_000L
        );
        String firstObjectKey = "auction-images/image-id-1.jpg";
        String secondObjectKey = "auction-images/image-id-2.png";
        PresignedPutObjectRequest firstPresignedRequest =
                mock(PresignedPutObjectRequest.class);
        PresignedPutObjectRequest secondPresignedRequest =
                mock(PresignedPutObjectRequest.class);

        when(objectKeyGenerator.generate(AuctionImageFileType.JPEG))
                .thenReturn(firstObjectKey);
        when(objectKeyGenerator.generate(AuctionImageFileType.PNG))
                .thenReturn(secondObjectKey);
        when(firstPresignedRequest.url()).thenReturn(
                URI.create("https://example.com/presigned-upload-1").toURL()
        );
        when(secondPresignedRequest.url()).thenReturn(
                URI.create("https://example.com/presigned-upload-2").toURL()
        );
        when(s3Presigner.presignPutObject(
                org.mockito.ArgumentMatchers.any(PutObjectPresignRequest.class)
        )).thenReturn(firstPresignedRequest, secondPresignedRequest);

        List<AuctionImagePresignResponse> responses = service.issue(
                memberId,
                draftId,
                List.of(firstRequest, secondRequest)
        );

        assertAll(
                () -> assertEquals(2, responses.size()),
                () -> assertEquals(
                        firstObjectKey,
                        responses.get(0).objectKey()
                ),
                () -> assertEquals(
                        secondObjectKey,
                        responses.get(1).objectKey()
                )
        );
        verify(s3Presigner, times(2))
                .presignPutObject(
                        org.mockito.ArgumentMatchers.any(PutObjectPresignRequest.class)
                );
        verify(pendingAuctionImageStore).saveAll(
                memberId,
                draftId,
                List.of(firstObjectKey, secondObjectKey)
        );
    }

    @Test
    void 지원하지_않는_이미지_형식이면_Presigned_URL을_발급하지_않는다() {
        S3Presigner s3Presigner = mock(S3Presigner.class);
        AuctionImageObjectKeyGenerator objectKeyGenerator =
                mock(AuctionImageObjectKeyGenerator.class);
        PendingAuctionImageStore pendingAuctionImageStore =
                mock(PendingAuctionImageStore.class);
        S3Properties properties = new S3Properties(
                BUCKET,
                "ap-northeast-2",
                PRESIGN_DURATION
        );
        AuctionImagePresignService service = new AuctionImagePresignService(
                s3Presigner,
                properties,
                objectKeyGenerator,
                pendingAuctionImageStore
        );
        AuctionImagePresignRequest request = new AuctionImagePresignRequest(
                "malware.exe",
                "application/octet-stream",
                1_024L
        );

        UploadException exception = assertThrows(
                UploadException.class,
                () -> service.issue(
                        1L,
                        UUID.fromString("44eac1aa-827d-40c3-b3e9-c44abb94ed09"),
                        List.of(request)
                )
        );

        assertEquals(ErrorCode.UNSUPPORTED_IMAGE_TYPE, exception.getErrorCode());
        verifyNoInteractions(
                objectKeyGenerator,
                s3Presigner,
                pendingAuctionImageStore
        );
    }
}
