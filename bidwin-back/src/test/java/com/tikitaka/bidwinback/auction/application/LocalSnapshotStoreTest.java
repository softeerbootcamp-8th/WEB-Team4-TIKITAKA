package com.tikitaka.bidwinback.auction.application;

import com.github.benmanes.caffeine.cache.Ticker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class LocalSnapshotStoreTest {

    private static final Duration RETENTION = Duration.ofMinutes(10);

    @Test
    void exact와_가장_최신_세대를_조회한다() {
        LocalSnapshotStore store = store(new AtomicLong());
        DownPriceSnapshot old = snapshot(LocalDateTime.of(2026, 8, 18, 12, 0));
        DownPriceSnapshot latest = snapshot(LocalDateTime.of(2026, 8, 18, 12, 0, 30));

        store.put(latest);
        store.put(old);

        assertThat(store.find(old.generationAt())).containsSame(old);
        assertThat(store.findLatest()).containsSame(latest);
    }

    @Test
    void 저장_후_10분이_지나면_exact와_latest에서_제거한다() {
        AtomicLong nanos = new AtomicLong();
        LocalSnapshotStore store = store(nanos);
        DownPriceSnapshot snapshot = snapshot(LocalDateTime.of(2026, 8, 18, 12, 0));
        store.put(snapshot);

        nanos.set(RETENTION.plusNanos(1).toNanos());

        assertThat(store.find(snapshot.generationAt())).isEmpty();
        assertThat(store.findLatest()).isEmpty();
    }

    private LocalSnapshotStore store(AtomicLong nanos) {
        Ticker ticker = () -> nanos.get();
        return new LocalSnapshotStore(
                new SimpleMeterRegistry(),
                RETENTION,
                ticker
        );
    }

    private DownPriceSnapshot snapshot(LocalDateTime generationAt) {
        return new DownPriceSnapshot(generationAt, List.of(), List.of());
    }
}
