package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSnapshotCircuitBreakerTest {

    @Test
    void 실패하면_5초간_호출을_차단하고_한_개의_복구_probe만_허용한다() {
        AtomicLong nanoTime = new AtomicLong();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RedisSnapshotCircuitBreaker circuitBreaker = new RedisSnapshotCircuitBreaker(
                Duration.ofSeconds(5),
                nanoTime::get,
                new DownPriceSnapshotMetrics(registry)
        );

        circuitBreaker.recordFailure();

        assertThat(circuitBreaker.tryAcquirePermission()).isFalse();
        assertThat(registry.get("snapshot.redis.circuit").gauge().value()).isEqualTo(1D);

        nanoTime.set(Duration.ofSeconds(5).toNanos());
        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
        assertThat(circuitBreaker.tryAcquirePermission()).isFalse();

        circuitBreaker.recordSuccess();
        assertThat(circuitBreaker.tryAcquirePermission()).isTrue();
        assertThat(registry.get("snapshot.redis.circuit").gauge().value()).isZero();
    }
}
