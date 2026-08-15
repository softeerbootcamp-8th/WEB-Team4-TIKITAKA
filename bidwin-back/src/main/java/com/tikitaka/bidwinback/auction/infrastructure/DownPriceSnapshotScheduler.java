package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.DownPriceSnapshot;
import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotCache;
import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DownPriceSnapshotScheduler {

    private final DownPriceSnapshotService downPriceSnapshotService;
    private final DownPriceSnapshotCache downPriceSnapshotCache;

    @Scheduled(
            fixedDelayString = "${app.auction.down-price-snapshot-interval}",
            initialDelay = 0,
            scheduler = DownPriceSnapshotSchedulingConfig.TASK_SCHEDULER
    )
    public void capture() {
        String owner = UUID.randomUUID().toString();
        if (!downPriceSnapshotCache.tryAcquireCaptureLock(owner)) {
            return;
        }

        try {
            DownPriceSnapshot snapshot = downPriceSnapshotService.capture();
            downPriceSnapshotCache.publish(snapshot);
        } catch (RuntimeException exception) {
            log.error("하향 경매 Redis 가격 스냅샷 적재 실패", exception);
        } finally {
            downPriceSnapshotCache.releaseCaptureLock(owner);
        }
    }
}
