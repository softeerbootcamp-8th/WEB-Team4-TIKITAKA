package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.global.config.PendingAuctionImageProperties;
import com.tikitaka.bidwinback.global.config.S3Properties;
import com.tikitaka.bidwinback.upload.domain.PendingAuctionImageStore;
import com.tikitaka.bidwinback.upload.domain.entity.PendingAuctionImage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PendingAuctionImageCleanupServiceTest {

    private static final String BUCKET = "bidwin-image-bucket";
    private static final Duration RETENTION = Duration.ofHours(24);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-30T03:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void 만료된_PENDING_이미지를_S3에서_먼저_삭제한_뒤_DB에서_삭제한다() {
        PendingAuctionImageStore store = mock(PendingAuctionImageStore.class);
        S3Client s3Client = mock(S3Client.class);
        PendingAuctionImageCleanupService service = createService(store, s3Client);
        PendingAuctionImage first = pendingImage("auction-images/first.jpg");
        PendingAuctionImage second = pendingImage("auction-images/second.jpg");
        LocalDateTime expectedCutoff = LocalDateTime.ofInstant(
                CLOCK.instant().minus(RETENTION),
                ZoneId.systemDefault()
        );

        when(store.findExpiredBefore(expectedCutoff, 1000))
                .thenReturn(List.of(first, second));
        when(s3Client.deleteObjects(
                org.mockito.ArgumentMatchers.any(DeleteObjectsRequest.class)
        )).thenReturn(DeleteObjectsResponse.builder().build());

        int deletedCount = service.cleanup();

        ArgumentCaptor<DeleteObjectsRequest> requestCaptor =
                ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(requestCaptor.capture());
        DeleteObjectsRequest request = requestCaptor.getValue();

        assertThat(deletedCount).isEqualTo(2);
        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.delete().objects())
                .extracting(object -> object.key())
                .containsExactly(
                        "auction-images/first.jpg",
                        "auction-images/second.jpg"
                );
        verify(store).deleteByObjectKeyIn(List.of(
                "auction-images/first.jpg",
                "auction-images/second.jpg"
        ));
    }

    @Test
    void S3_삭제에_실패한_이미지는_DB에_남겨_다음_실행에서_재시도한다() {
        PendingAuctionImageStore store = mock(PendingAuctionImageStore.class);
        S3Client s3Client = mock(S3Client.class);
        PendingAuctionImageCleanupService service = createService(store, s3Client);
        PendingAuctionImage first = pendingImage("auction-images/first.jpg");
        PendingAuctionImage second = pendingImage("auction-images/second.jpg");

        when(store.findExpiredBefore(
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(1000)
        )).thenReturn(List.of(first, second));
        when(s3Client.deleteObjects(
                org.mockito.ArgumentMatchers.any(DeleteObjectsRequest.class)
        )).thenReturn(DeleteObjectsResponse.builder()
                .errors(software.amazon.awssdk.services.s3.model.S3Error.builder()
                        .key("auction-images/second.jpg")
                        .code("InternalError")
                        .build())
                .build());

        int deletedCount = service.cleanup();

        assertThat(deletedCount).isOne();
        verify(store).deleteByObjectKeyIn(List.of("auction-images/first.jpg"));
    }

    @Test
    void 만료된_이미지가_없으면_S3를_호출하지_않는다() {
        PendingAuctionImageStore store = mock(PendingAuctionImageStore.class);
        S3Client s3Client = mock(S3Client.class);
        PendingAuctionImageCleanupService service = createService(store, s3Client);

        when(store.findExpiredBefore(
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(1000)
        )).thenReturn(List.of());

        int deletedCount = service.cleanup();

        assertThat(deletedCount).isZero();
        verifyNoInteractions(s3Client);
        verify(store, never()).deleteByObjectKeyIn(
                org.mockito.ArgumentMatchers.anyList()
        );
    }

    private PendingAuctionImageCleanupService createService(
            PendingAuctionImageStore store,
            S3Client s3Client
    ) {
        return new PendingAuctionImageCleanupService(
                store,
                s3Client,
                new S3Properties(BUCKET, "ap-northeast-2", Duration.ofMinutes(5)),
                new PendingAuctionImageProperties(RETENTION, 1000),
                CLOCK
        );
    }

    private PendingAuctionImage pendingImage(String objectKey) {
        return PendingAuctionImage.issue(
                1L,
                UUID.fromString("44eac1aa-827d-40c3-b3e9-c44abb94ed09"),
                objectKey
        );
    }
}
