package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.global.storage.ObjectStorage;
import com.tikitaka.bidwinback.global.storage.PresignedUpload;
import com.tikitaka.bidwinback.upload.domain.AuctionImageFileType;
import com.tikitaka.bidwinback.upload.domain.PendingAuctionImageStore;
import com.tikitaka.bidwinback.upload.domain.UploadException;
import com.tikitaka.bidwinback.upload.presentation.dto.AuctionImagePresignRequest;
import com.tikitaka.bidwinback.upload.presentation.dto.AuctionImagePresignResponse;
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
        AuctionImagePresignRequest request = new AuctionImagePresignRequest(
                "headphone.jpeg",
                "image/jpeg",
                248_392L
        );
        String objectKey = "auction-images/image-id.jpg";
        String presignedUrl = "https://example.com/presigned-upload";
        Instant expiresAt = Instant.parse("2026-07-28T06:10:00Z");
        Map<String, List<String>> signedHeaders = Map.of(
                "Content-Type",
                List.of("image/jpeg")
        );

        when(objectKeyGenerator.generate(AuctionImageFileType.JPEG))
                .thenReturn(objectKey);
        when(objectStorage.presignPut(objectKey, "image/jpeg", request.size()))
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
                () -> assertEquals(presignedUrl, response.presignedUrl()),
                () -> assertEquals(objectKey, response.objectKey()),
                () -> assertEquals(signedHeaders, response.signedHeaders()),
                () -> assertEquals(expiresAt, response.expiresAt())
        );
        verify(objectKeyGenerator).generate(AuctionImageFileType.JPEG);
        verify(objectStorage).presignPut(objectKey, "image/jpeg", request.size());
        verify(pendingAuctionImageStore).saveAll(
                memberId,
                draftId,
                List.of(objectKey)
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

        when(objectKeyGenerator.generate(AuctionImageFileType.JPEG))
                .thenReturn(firstObjectKey);
        when(objectKeyGenerator.generate(AuctionImageFileType.PNG))
                .thenReturn(secondObjectKey);
        when(objectStorage.presignPut(
                firstObjectKey,
                "image/jpeg",
                firstRequest.size()
        )).thenReturn(new PresignedUpload(
                "https://example.com/presigned-upload-1",
                Map.of(),
                Instant.parse("2026-07-28T06:10:00Z")
        ));
        when(objectStorage.presignPut(
                secondObjectKey,
                "image/png",
                secondRequest.size()
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
                        firstObjectKey,
                        responses.get(0).objectKey()
                ),
                () -> assertEquals(
                        secondObjectKey,
                        responses.get(1).objectKey()
                )
        );
        verify(objectStorage).presignPut(
                firstObjectKey,
                "image/jpeg",
                firstRequest.size()
        );
        verify(objectStorage).presignPut(
                secondObjectKey,
                "image/png",
                secondRequest.size()
        );
        verify(pendingAuctionImageStore).saveAll(
                memberId,
                draftId,
                List.of(firstObjectKey, secondObjectKey)
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
                objectStorage,
                pendingAuctionImageStore
        );
    }
}
