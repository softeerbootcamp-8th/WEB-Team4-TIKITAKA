package com.tikitaka.bidwinback.auction.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class LocalSnapshotStore {

    private static final String CACHE_NAME = "downPriceSnapshotLocal";
    private static final int MAXIMUM_GENERATIONS = 20;

    private final Cache<LocalDateTime, DownPriceSnapshot> snapshots;
    private final AtomicReference<LocalDateTime> latestGeneration = new AtomicReference<>();

    @Autowired
    public LocalSnapshotStore(
            MeterRegistry meterRegistry,
            @Value("${app.auction.down-price-snapshot.retention}") Duration retention
    ) {
        this(meterRegistry, retention, Ticker.systemTicker());
    }

    LocalSnapshotStore(
            MeterRegistry meterRegistry,
            Duration retention,
            Ticker ticker
    ) {
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("로컬 스냅샷 보존 기간은 0보다 커야 합니다.");
        }
        snapshots = Caffeine.newBuilder()
                .maximumSize(MAXIMUM_GENERATIONS)
                .expireAfterWrite(retention)
                .ticker(ticker)
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(meterRegistry, snapshots, CACHE_NAME);
    }

    public void put(DownPriceSnapshot snapshot) {
        snapshots.put(snapshot.generationAt(), snapshot);
        latestGeneration.accumulateAndGet(
                snapshot.generationAt(),
                (current, candidate) -> current == null || candidate.isAfter(current)
                        ? candidate
                        : current
        );
    }

    public Optional<DownPriceSnapshot> find(LocalDateTime generationAt) {
        return Optional.ofNullable(snapshots.getIfPresent(generationAt));
    }

    public Optional<DownPriceSnapshot> findLatest() {
        LocalDateTime generationAt = latestGeneration.get();
        if (generationAt == null) {
            return Optional.empty();
        }
        DownPriceSnapshot snapshot = snapshots.getIfPresent(generationAt);
        if (snapshot == null) {
            latestGeneration.compareAndSet(generationAt, null);
        }
        return Optional.ofNullable(snapshot);
    }
}
