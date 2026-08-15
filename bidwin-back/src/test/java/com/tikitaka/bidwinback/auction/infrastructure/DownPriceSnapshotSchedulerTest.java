package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.DownPriceSnapshot;
import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotCache;
import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DownPriceSnapshotSchedulerTest {

    @Mock
    private DownPriceSnapshotService service;

    @Mock
    private DownPriceSnapshotCache cache;

    private DownPriceSnapshotScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DownPriceSnapshotScheduler(service, cache);
    }

    @Test
    void 다른_인스턴스가_락을_가졌으면_스냅샷을_만들지_않는다() {
        when(cache.tryAcquireCaptureLock(anyString())).thenReturn(false);

        scheduler.capture();

        verify(service, never()).capture();
        verify(cache, never()).publish(org.mockito.ArgumentMatchers.any());
        verify(cache, never()).releaseCaptureLock(anyString());
    }

    @Test
    void 락을_얻은_인스턴스만_스냅샷을_발행하고_락을_해제한다() {
        DownPriceSnapshot snapshot = new DownPriceSnapshot(
                LocalDateTime.of(2026, 8, 15, 10, 0),
                0L,
                List.of(),
                List.of()
        );
        when(cache.tryAcquireCaptureLock(anyString())).thenReturn(true);
        when(service.capture()).thenReturn(snapshot);

        scheduler.capture();

        InOrder order = inOrder(cache, service);
        order.verify(cache).tryAcquireCaptureLock(anyString());
        order.verify(service).capture();
        order.verify(cache).publish(snapshot);
        order.verify(cache).releaseCaptureLock(anyString());
    }

    @Test
    void 적재가_실패해도_락을_해제하고_다음_스케줄을_막지_않는다() {
        when(cache.tryAcquireCaptureLock(anyString())).thenReturn(true);
        when(service.capture()).thenThrow(new IllegalStateException("적재 실패"));

        scheduler.capture();

        verify(cache).releaseCaptureLock(anyString());
    }
}
