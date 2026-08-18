package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.global.storage.ObjectStorage;
import com.tikitaka.bidwinback.global.storage.PresignedUpload;
import com.tikitaka.bidwinback.upload.domain.AuctionImageUploadReservation;
import com.tikitaka.bidwinback.upload.domain.exception.UploadException;
import com.tikitaka.bidwinback.upload.domain.repository.PendingAuctionImageStore;
import com.tikitaka.bidwinback.upload.presentation.dto.request.AuctionImagePresignRequest;
import com.tikitaka.bidwinback.upload.presentation.dto.response.AuctionImagePresignResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuctionImagePresignServiceTest {

    @Test
    void 이미지_업로드용_Presigned_URL과_서명된_헤더를_발급한다() {
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        AuctionImageObjectKeyGenerator objectKeyGenerator =
                mock(AuctionImageObjectKeyGenerator.class);
        PendingAuctionImageStore pendingAuctionImageStore =
                mock(PendingAuctionImageStore.class);
        AuctionImagePresignService service = new AuctionImagePresignService(
                objectStorage,
                objectKeyGenerator,
                pendingAuctionImageStore
        );
        long memberId = 1L;
        UUID draftId = UUID.fromString("44eac1aa-827d-40c3-b3e9-c44abb94ed09");
        String checksumSha256 = "mH2LpXfVw2f2Y87a5SIc1J5m3eS74iqrAx/CBEhb3c4=";
        UUID uploadId = UUID.fromString("a2ddf707-cc3b-43d0-8c92-b86e8da74bc6");
        AuctionImagePresignRequest request = new AuctionImagePresignRequest(
                "headphone.jpeg",
                "image/jpeg",
                248_392L,
                checksumSha256
        );
        String objectKey = "temp/a2ddf707-cc3b-43d0-8c92-b86e8da74bc6";
        String presignedUrl = "https://example.com/presigned-upload";
        Instant expiresAt = Instant.parse("2026-07-28T06:10:00Z");
        Map<String, List<String>> signedHeaders = Map.of(
                "Content-Type",
                List.of("image/jpeg")
        );

        when(objectKeyGenerator.generateUploadId()).thenReturn(uploadId);
        when(objectKeyGenerator.generateTemporary(uploadId))
                .thenReturn(objectKey);
        when(objectStorage.presignPut(
                objectKey,
                "image/jpeg",
                request.size(),
                checksumSha256
        ))
                .thenReturn(new PresignedUpload(
                        presignedUrl,
                        signedHeaders,
                        expiresAt
                ));

        AuctionImagePresignResponse response = service.issue(
                memberId,
                draftId,
                List.of(request)
        ).getFirst();

        assertAll(
                () -> assertEquals(uploadId, response.uploadId()),
                () -> assertEquals(presignedUrl, response.presignedUrl()),
                () -> assertEquals(signedHeaders, response.signedHeaders()),
                () -> assertEquals(expiresAt, response.expiresAt())
        );
        verify(objectKeyGenerator).generateUploadId();
        verify(objectKeyGenerator).generateTemporary(uploadId);
        verify(objectStorage).presignPut(
                objectKey,
                "image/jpeg",
                request.size(),
                checksumSha256
        );
        verify(pendingAuctionImageStore).saveAll(
                memberId,
                draftId,
                List.of(new AuctionImageUploadReservation(
                        uploadId,
                        objectKey,
                        "image/jpeg",
                        request.size(),
                        checksumSha256
                ))
        );
    }

    @Test
    void 여러_이미지의_Presigned_URL을_요청_순서대로_발급한다() {
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        AuctionImageObjectKeyGenerator objectKeyGenerator =
                mock(AuctionImageObjectKeyGenerator.class);
        PendingAuctionImageStore pendingAuctionImageStore =
                mock(PendingAuctionImageStore.class);
        AuctionImagePresignService service = new AuctionImagePresignService(
                objectStorage,
                objectKeyGenerator,
                pendingAuctionImageStore
        );
        long memberId = 1L;
        UUID draftId = UUID.fromString("44eac1aa-827d-40c3-b3e9-c44abb94ed09");
        String firstChecksum = "mH2LpXfVw2f2Y87a5SIc1J5m3eS74iqrAx/CBEhb3c4=";
        String secondChecksum = "YgVvBrlqJ7qG0u/UokhAn3lVnI5PThR2Y7Nk2cQ7QzE=";
        UUID firstUploadId = UUID.fromString("a2ddf707-cc3b-43d0-8c92-b86e8da74bc6");
        UUID secondUploadId = UUID.fromString("f6822a2e-d7ad-4896-a801-1524c81eb6b2");
        AuctionImagePresignRequest firstRequest = new AuctionImagePresignRequest(
                "headphone.jpg",
                "image/jpeg",
                248_392L,
                firstChecksum
        );
        AuctionImagePresignRequest secondRequest = new AuctionImagePresignRequest(
                "keyboard.png",
                "image/png",
                128_000L,
                secondChecksum
        );
        String firstObjectKey = "temp/a2ddf707-cc3b-43d0-8c92-b86e8da74bc6";
        String secondObjectKey = "temp/f6822a2e-d7ad-4896-a801-1524c81eb6b2";

        when(objectKeyGenerator.generateUploadId()).thenReturn(firstUploadId, secondUploadId);
        when(objectKeyGenerator.generateTemporary(firstUploadId))
                .thenReturn(firstObjectKey);
        when(objectKeyGenerator.generateTemporary(secondUploadId))
                .thenReturn(secondObjectKey);
        when(objectStorage.presignPut(
                firstObjectKey,
                "image/jpeg",
                firstRequest.size(),
                firstChecksum
        )).thenReturn(new PresignedUpload(
                "https://example.com/presigned-upload-1",
                Map.of(),
                Instant.parse("2026-07-28T06:10:00Z")
        ));
        when(objectStorage.presignPut(
                secondObjectKey,
                "image/png",
                secondRequest.size(),
                secondChecksum
        )).thenReturn(new PresignedUpload(
                "https://example.com/presigned-upload-2",
                Map.of(),
                Instant.parse("2026-07-28T06:10:00Z")
        ));

        List<AuctionImagePresignResponse> responses = service.issue(
                memberId,
                draftId,
                List.of(firstRequest, secondRequest)
        );

        assertAll(
                () -> assertEquals(2, responses.size()),
                () -> assertEquals(
                        firstUploadId,
                        responses.get(0).uploadId()
                ),
                () -> assertEquals(
                        secondUploadId,
                        responses.get(1).uploadId()
                )
        );
        verify(objectStorage).presignPut(
                firstObjectKey,
                "image/jpeg",
                firstRequest.size(),
                firstChecksum
        );
        verify(objectStorage).presignPut(
                secondObjectKey,
                "image/png",
                secondRequest.size(),
                secondChecksum
        );
        verify(pendingAuctionImageStore).saveAll(
                memberId,
                draftId,
                List.of(
                        new AuctionImageUploadReservation(
                                firstUploadId, firstObjectKey, "image/jpeg",
                                firstRequest.size(), firstChecksum
                        ),
                        new AuctionImageUploadReservation(
                                secondUploadId, secondObjectKey, "image/png",
                                secondRequest.size(), secondChecksum
                        )
                )
        );
    }

    @Test
    void 지원하지_않는_이미지_형식이면_Presigned_URL을_발급하지_않는다() {
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        AuctionImageObjectKeyGenerator objectKeyGenerator =
                mock(AuctionImageObjectKeyGenerator.class);
        PendingAuctionImageStore pendingAuctionImageStore =
                mock(PendingAuctionImageStore.class);
        AuctionImagePresignService service = new AuctionImagePresignService(
                objectStorage,
                objectKeyGenerator,
                pendingAuctionImageStore
        );
        AuctionImagePresignRequest request = new AuctionImagePresignRequest(
                "malware.exe",
                "application/octet-stream",
                1_024L,
                "mH2LpXfVw2f2Y87a5SIc1J5m3eS74iqrAx/CBEhb3c4="
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
                objectStorage,
                pendingAuctionImageStore
        );
    }
}
