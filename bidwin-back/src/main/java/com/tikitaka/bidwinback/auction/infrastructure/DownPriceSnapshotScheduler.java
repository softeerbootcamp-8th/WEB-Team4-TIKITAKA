package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.DownPriceSnapshot;
import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotCache;
import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DownPriceSnapshotScheduler {

    private static final String CAPTURE_METRIC = "auction.down.price.snapshot.capture";
    private static final String TAG_RESULT = "result";
    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_FAILURE = "failure";

    private final DownPriceSnapshotService downPriceSnapshotService;
    private final DownPriceSnapshotCache downPriceSnapshotCache;
    private final Timer captureSuccess;
    private final Timer captureFailure;

    public DownPriceSnapshotScheduler(
            DownPriceSnapshotService downPriceSnapshotService,
            DownPriceSnapshotCache downPriceSnapshotCache,
            MeterRegistry meterRegistry
    ) {
        this.downPriceSnapshotService = downPriceSnapshotService;
        this.downPriceSnapshotCache = downPriceSnapshotCache;
        this.captureSuccess = captureTimer(meterRegistry, RESULT_SUCCESS);
        this.captureFailure = captureTimer(meterRegistry, RESULT_FAILURE);
    }

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

        long startedAt = System.nanoTime();
        try {
            DownPriceSnapshot snapshot = downPriceSnapshotService.capture();
            downPriceSnapshotCache.publish(snapshot);
            captureSuccess.record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
        } catch (RuntimeException exception) {
            captureFailure.record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
            log.error("하향 경매 Redis 가격 스냅샷 적재 실패", exception);
        } finally {
            downPriceSnapshotCache.releaseCaptureLock(owner);
        }
    }

    private Timer captureTimer(MeterRegistry meterRegistry, String result) {
        return Timer.builder(CAPTURE_METRIC)
                .description("하향 경매 Redis 가격 스냅샷 생성과 발행 소요시간")
                .tag(TAG_RESULT, result)
                .register(meterRegistry);
    }
}
