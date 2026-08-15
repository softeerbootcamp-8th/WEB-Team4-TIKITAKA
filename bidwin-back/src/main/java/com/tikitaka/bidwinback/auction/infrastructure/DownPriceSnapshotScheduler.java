package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotCountCache;
import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class DownPriceSnapshotScheduler {

    private final DownPriceSnapshotService downPriceSnapshotService;
    private final DownPriceSnapshotCountCache downPriceSnapshotCountCache;
    private final Duration retention;

    public DownPriceSnapshotScheduler(
            DownPriceSnapshotService downPriceSnapshotService,
            DownPriceSnapshotCountCache downPriceSnapshotCountCache,
            @Value("${app.auction.down-price-snapshot-retention}") Duration retention
    ) {
        this.downPriceSnapshotService = downPriceSnapshotService;
        this.downPriceSnapshotCountCache = downPriceSnapshotCountCache;
        this.retention = retention;
    }

    @Scheduled(
            fixedDelayString = "${app.auction.down-price-snapshot-interval}",
            initialDelay = 0
    )
    public void capture() {
        try {
            DownPriceSnapshotService.CaptureResult result = downPriceSnapshotService.capture();
            downPriceSnapshotCountCache.put(result.snapshotAt(), result.count());
        } catch (RuntimeException exception) {
            log.error("하향 경매 가격 스냅샷 적재 실패", exception);
        }

        try {
            downPriceSnapshotService.deleteOlderThan(retention);
        } catch (RuntimeException exception) {
            log.error("하향 경매 가격 스냅샷 정리 실패", exception);
        }
    }
}
