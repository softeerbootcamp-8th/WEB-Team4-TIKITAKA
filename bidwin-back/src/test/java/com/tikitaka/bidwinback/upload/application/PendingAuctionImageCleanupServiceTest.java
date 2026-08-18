package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.global.config.PendingAuctionImageProperties;
import com.tikitaka.bidwinback.upload.domain.entity.PendingAuctionImage;
import com.tikitaka.bidwinback.upload.domain.repository.PendingAuctionImageStore;
import org.junit.jupiter.api.Test;

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
import static org.mockito.Mockito.when;

class PendingAuctionImageCleanupServiceTest {

    private static final Duration RETENTION = Duration.ofHours(24);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-30T03:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void 만료된_PENDING_이미지의_예약_행을_DB에서_삭제한다() {
        PendingAuctionImageStore store = mock(PendingAuctionImageStore.class);
        PendingAuctionImageCleanupService service = createService(store);
        PendingAuctionImage first = pendingImage("temp/first");
        PendingAuctionImage second = pendingImage("temp/second");
        List<String> objectKeys = List.of(
                "temp/first",
                "temp/second"
        );
        LocalDateTime expectedCutoff = LocalDateTime.ofInstant(
                CLOCK.instant().minus(RETENTION),
                ZoneId.systemDefault()
        );

        when(store.findExpiredBefore(expectedCutoff, 1000))
                .thenReturn(List.of(first, second));

        int deletedCount = service.cleanup();

        assertThat(deletedCount).isEqualTo(2);
        verify(store).deleteByObjectKeyIn(objectKeys);
    }

    @Test
    void 만료된_예약이_없으면_DB를_삭제하지_않는다() {
        PendingAuctionImageStore store = mock(PendingAuctionImageStore.class);
        PendingAuctionImageCleanupService service = createService(store);

        when(store.findExpiredBefore(
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(1000)
        )).thenReturn(List.of());

        int deletedCount = service.cleanup();

        assertThat(deletedCount).isZero();
        verify(store, never()).deleteByObjectKeyIn(
                org.mockito.ArgumentMatchers.anyList()
        );
    }

    private PendingAuctionImageCleanupService createService(PendingAuctionImageStore store) {
        return new PendingAuctionImageCleanupService(
                store,
                new PendingAuctionImageProperties(RETENTION, 1000),
                CLOCK
        );
    }

    private PendingAuctionImage pendingImage(String objectKey) {
        return PendingAuctionImage.issue(
                1L,
                UUID.fromString("44eac1aa-827d-40c3-b3e9-c44abb94ed09"),
                UUID.randomUUID(),
                objectKey,
                "image/jpeg",
                248_392L,
                "mH2LpXfVw2f2Y87a5SIc1J5m3eS74iqrAx/CBEhb3c4="
        );
    }
}
