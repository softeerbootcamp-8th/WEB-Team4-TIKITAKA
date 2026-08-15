package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotCountCache;
import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DownPriceSnapshotSchedulerTest {

    private static final Duration RETENTION = Duration.ofMinutes(10);

    @Mock
    private DownPriceSnapshotService downPriceSnapshotService;

    @Mock
    private DownPriceSnapshotCountCache downPriceSnapshotCountCache;

    private DownPriceSnapshotScheduler downPriceSnapshotScheduler;

    private static final LocalDateTime SNAPSHOT_AT =
            LocalDateTime.of(2026, 8, 14, 12, 0, 0, 123_000_000);
    private static final int CAPTURED_COUNT = 3;

    @BeforeEach
    void setUp() {
        lenient().when(downPriceSnapshotService.capture())
                .thenReturn(new DownPriceSnapshotService.CaptureResult(SNAPSHOT_AT, CAPTURED_COUNT));
        downPriceSnapshotScheduler = new DownPriceSnapshotScheduler(
                downPriceSnapshotService,
                downPriceSnapshotCountCache,
                RETENTION
        );
    }

    @Test
    void 적재가_실패해도_정리를_시도하고_예외를_전파하지_않는다() {
        doThrow(new RuntimeException("capture failed"))
                .when(downPriceSnapshotService).capture();

        assertThatCode(downPriceSnapshotScheduler::capture).doesNotThrowAnyException();

        verify(downPriceSnapshotService).capture();
        verify(downPriceSnapshotCountCache, never()).put(SNAPSHOT_AT, CAPTURED_COUNT);
        verify(downPriceSnapshotService).deleteOlderThan(RETENTION);
    }

    @Test
    void 적재_성공_후_count를_캐시에_미리_채우고_정리를_시도한다() {
        downPriceSnapshotScheduler.capture();

        verify(downPriceSnapshotCountCache).put(SNAPSHOT_AT, CAPTURED_COUNT);
        verify(downPriceSnapshotService).deleteOlderThan(RETENTION);
    }

    @Test
    void 캐시_prewarm이_실패해도_정리를_시도하고_예외를_전파하지_않는다() {
        doThrow(new RuntimeException("cache prewarm failed"))
                .when(downPriceSnapshotCountCache).put(SNAPSHOT_AT, CAPTURED_COUNT);

        assertThatCode(downPriceSnapshotScheduler::capture).doesNotThrowAnyException();

        verify(downPriceSnapshotService).capture();
        verify(downPriceSnapshotService).deleteOlderThan(RETENTION);
    }

    @Test
    void 정리가_실패해도_예외를_전파하지_않는다() {
        doThrow(new RuntimeException("cleanup failed"))
                .when(downPriceSnapshotService).deleteOlderThan(RETENTION);

        assertThatCode(downPriceSnapshotScheduler::capture).doesNotThrowAnyException();

        verify(downPriceSnapshotService).capture();
        verify(downPriceSnapshotService).deleteOlderThan(RETENTION);
    }
}
