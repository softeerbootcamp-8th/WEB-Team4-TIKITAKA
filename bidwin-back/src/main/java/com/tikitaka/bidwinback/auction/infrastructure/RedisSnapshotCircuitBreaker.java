package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.LongSupplier;

@Component
public class RedisSnapshotCircuitBreaker {

    private final long openDurationNanos;
    private final LongSupplier nanoTime;
    private final DownPriceSnapshotMetrics metrics;

    private long openUntilNanos;
    private boolean probeInProgress;

    @Autowired
    public RedisSnapshotCircuitBreaker(
            @Value("${app.auction.down-price-snapshot.redis-circuit-open-duration}")
            Duration openDuration,
            DownPriceSnapshotMetrics metrics
    ) {
        this(openDuration, System::nanoTime, metrics);
    }

    RedisSnapshotCircuitBreaker(
            Duration openDuration,
            LongSupplier nanoTime,
            DownPriceSnapshotMetrics metrics
    ) {
        if (openDuration.isNegative() || openDuration.isZero()) {
            throw new IllegalArgumentException("Redis circuit open 기간은 0보다 커야 합니다.");
        }
        this.openDurationNanos = openDuration.toNanos();
        this.nanoTime = nanoTime;
        this.metrics = metrics;
    }

    public synchronized boolean tryAcquirePermission() {
        if (openUntilNanos == 0L) {
            return true;
        }
        if (nanoTime.getAsLong() < openUntilNanos || probeInProgress) {
            return false;
        }
        probeInProgress = true;
        return true;
    }

    public synchronized void recordSuccess() {
        openUntilNanos = 0L;
        probeInProgress = false;
        metrics.setRedisCircuitOpen(false);
    }

    public synchronized void recordFailure() {
        long now = nanoTime.getAsLong();
        openUntilNanos = now > Long.MAX_VALUE - openDurationNanos
                ? Long.MAX_VALUE
                : now + openDurationNanos;
        probeInProgress = false;
        metrics.setRedisCircuitOpen(true);
    }
}
