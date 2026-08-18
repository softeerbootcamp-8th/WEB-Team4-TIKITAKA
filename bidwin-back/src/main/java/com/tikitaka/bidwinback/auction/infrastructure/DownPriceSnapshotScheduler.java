package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.AuctionDatabaseTimeQuery;
import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotBuildCoordinator;
import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotMetrics;
import com.tikitaka.bidwinback.auction.application.SnapshotBuildKey;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DownPriceSnapshotScheduler {

    private final AuctionDatabaseTimeQuery databaseTimeQuery;
    private final RedisSnapshotStore redisStore;
    private final DownPriceSnapshotBuildCoordinator buildCoordinator;
    private final DownPriceSnapshotMetrics metrics;

    @Scheduled(
            fixedRateString = "${app.auction.down-price-snapshot.refresh-interval}",
            initialDelay = 0,
            scheduler = DownPriceSnapshotSchedulingConfig.TASK_SCHEDULER
    )
    public void refreshLatest() {
        LocalDateTime databaseTime;
        SnapshotBuildKey key;
        try {
            databaseTime = databaseTimeQuery.currentTime();
            key = SnapshotBuildKey.latestSlot(databaseTime);
        } catch (RuntimeException exception) {
            log.error("하향 가격 스냅샷 세대 시각을 조회하지 못했습니다.", exception);
            return;
        }

        try {
            boolean lockAcquired = redisStore.tryAcquireCaptureLock(key);
            redisStore.refreshEvictionMetric();
            if (!lockAcquired) {
                redisStore.findLatestPage(AuctionSort.PRICE_LOW, 1, 1)
                        .ifPresent(page -> metrics.recordGenerationAge(Duration.between(
                                page.generationAt(),
                                databaseTime
                        )));
                return;
            }
        } catch (RedisSnapshotUnavailableException exception) {
            log.warn("Redis 장애로 로컬 하향 가격 스냅샷을 생성합니다.", exception);
        }

        buildCoordinator.getOrBuild(key)
                .whenComplete((snapshot, exception) -> {
                    if (exception == null) {
                        metrics.recordGenerationAge(Duration.between(
                                snapshot.generationAt(),
                                databaseTime
                        ));
                        return;
                    }
                    log.error(
                            "하향 가격 스냅샷 생성에 실패해 이전 세대를 유지합니다. generationAt={}",
                            key.generationAt(),
                            exception
                    );
                });
    }
}
