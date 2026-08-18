package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.AuctionDatabaseTimeQuery;
import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotBuildCoordinator;
import com.tikitaka.bidwinback.auction.application.SnapshotBuildKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DownPriceSnapshotScheduler {

    private final AuctionDatabaseTimeQuery databaseTimeQuery;
    private final RedisSnapshotStore redisStore;
    private final DownPriceSnapshotBuildCoordinator buildCoordinator;

    @Scheduled(
            fixedRateString = "${app.auction.down-price-snapshot.refresh-interval}",
            initialDelay = 0,
            scheduler = DownPriceSnapshotSchedulingConfig.TASK_SCHEDULER
    )
    public void refreshLatest() {
        SnapshotBuildKey key;
        try {
            key = SnapshotBuildKey.latestSlot(databaseTimeQuery.currentTime());
        } catch (RuntimeException exception) {
            log.error("하향 가격 스냅샷 세대 시각을 조회하지 못했습니다.", exception);
            return;
        }

        try {
            if (!redisStore.tryAcquireCaptureLock(key)) {
                return;
            }
        } catch (RedisSnapshotUnavailableException exception) {
            log.warn("Redis 장애로 로컬 하향 가격 스냅샷을 생성합니다.", exception);
        }

        buildCoordinator.getOrBuild(key)
                .exceptionally(exception -> {
                    log.error(
                            "하향 가격 스냅샷 생성에 실패해 이전 세대를 유지합니다. generationAt={}",
                            key.generationAt(),
                            exception
                    );
                    return null;
                });
    }
}
