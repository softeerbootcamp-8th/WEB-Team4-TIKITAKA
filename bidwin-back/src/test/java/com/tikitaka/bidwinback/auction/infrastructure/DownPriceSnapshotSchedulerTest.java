package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.AuctionDatabaseTimeQuery;
import com.tikitaka.bidwinback.auction.application.DownPriceSnapshot;
import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotBuildCoordinator;
import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotMetrics;
import com.tikitaka.bidwinback.auction.application.SnapshotBuildKey;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DownPriceSnapshotSchedulerTest {

    @Mock
    private AuctionDatabaseTimeQuery databaseTimeQuery;

    @Mock
    private RedisSnapshotStore redisStore;

    @Mock
    private DownPriceSnapshotBuildCoordinator buildCoordinator;

    private final DownPriceSnapshotMetrics metrics =
            new DownPriceSnapshotMetrics(new SimpleMeterRegistry());

    @Test
    void 같은_30초_슬롯의_분산락을_얻은_인스턴스만_Coordinator를_호출한다() {
        LocalDateTime databaseTime = LocalDateTime.of(2026, 8, 18, 12, 0, 47);
        SnapshotBuildKey key = SnapshotBuildKey.latestSlot(databaseTime);
        when(databaseTimeQuery.currentTime()).thenReturn(databaseTime);
        when(redisStore.tryAcquireCaptureLock(key)).thenReturn(true);
        when(buildCoordinator.getOrBuild(key)).thenReturn(CompletableFuture.completedFuture(
                new DownPriceSnapshot(key.generationAt(), List.of(), List.of())
        ));

        new DownPriceSnapshotScheduler(databaseTimeQuery, redisStore, buildCoordinator, metrics)
                .refreshLatest();

        verify(buildCoordinator).getOrBuild(key);
        verify(redisStore).refreshEvictionMetric();
    }

    @Test
    void 다른_인스턴스가_슬롯락을_가졌으면_세대를_만들지_않는다() {
        LocalDateTime databaseTime = LocalDateTime.of(2026, 8, 18, 12, 0, 47);
        SnapshotBuildKey key = SnapshotBuildKey.latestSlot(databaseTime);
        when(databaseTimeQuery.currentTime()).thenReturn(databaseTime);
        when(redisStore.tryAcquireCaptureLock(key)).thenReturn(false);

        new DownPriceSnapshotScheduler(databaseTimeQuery, redisStore, buildCoordinator, metrics)
                .refreshLatest();

        verify(buildCoordinator, never()).getOrBuild(key);
        verify(redisStore).refreshEvictionMetric();
    }
}
