package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.global.config.S3Properties;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.upload.domain.AuctionImageFileType;
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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuctionImagePresignServiceTest {

    private static final String BUCKET = "bidwin-image-bucket";
    private static final Duration PRESIGN_DURATION = Duration.ofMinutes(5);

    @Test
    void 이미지_업로드용_Presigned_URL을_발급한다() throws Exception {
        S3Presigner s3Presigner = mock(S3Presigner.class);
        AuctionImageObjectKeyGenerator objectKeyGenerator =
                mock(AuctionImageObjectKeyGenerator.class);
        S3Properties properties = new S3Properties(
                BUCKET,
                "ap-northeast-2",
                PRESIGN_DURATION
        );
        AuctionImagePresignService service = new AuctionImagePresignService(
                s3Presigner,
                properties,
                objectKeyGenerator
        );
        AuctionImagePresignRequest request = new AuctionImagePresignRequest(
                "headphone.jpeg",
                "image/jpeg",
                248_392L
        );
        String objectKey = "auction-images/image-id.jpg";
        URL presignedUrl = URI.create("https://example.com/presigned-upload").toURL();
        Instant expiresAt = Instant.parse("2026-07-28T06:10:00Z");
        PresignedPutObjectRequest presignedRequest =
                mock(PresignedPutObjectRequest.class);

        when(objectKeyGenerator.generate(AuctionImageFileType.JPEG))
                .thenReturn(objectKey);
        when(presignedRequest.url()).thenReturn(presignedUrl);
        when(presignedRequest.expiration()).thenReturn(expiresAt);
        when(s3Presigner.presignPutObject(
                org.mockito.ArgumentMatchers.any(PutObjectPresignRequest.class)
        )).thenReturn(presignedRequest);

        AuctionImagePresignResponse response = service.issue(request);

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
                        request.size(),
                        capturedPutObjectRequest.contentLength()
                )
        );
        verify(objectKeyGenerator).generate(AuctionImageFileType.JPEG);
    }

    @Test
    void 지원하지_않는_이미지_형식이면_Presigned_URL을_발급하지_않는다() {
        S3Presigner s3Presigner = mock(S3Presigner.class);
        AuctionImageObjectKeyGenerator objectKeyGenerator =
                mock(AuctionImageObjectKeyGenerator.class);
        S3Properties properties = new S3Properties(
                BUCKET,
                "ap-northeast-2",
                PRESIGN_DURATION
        );
        AuctionImagePresignService service = new AuctionImagePresignService(
                s3Presigner,
                properties,
                objectKeyGenerator
        );
        AuctionImagePresignRequest request = new AuctionImagePresignRequest(
                "malware.exe",
                "application/octet-stream",
                1_024L
        );

        UploadException exception = assertThrows(
                UploadException.class,
                () -> service.issue(request)
        );

        assertEquals(ErrorCode.UNSUPPORTED_IMAGE_TYPE, exception.getErrorCode());
        verifyNoInteractions(objectKeyGenerator, s3Presigner);
    }
}
