package com.tikitaka.bidwinback.auction.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DownPriceSnapshotMetricsTest {

    @Test
    void 생성된_세대가_없어도_애플리케이션_시작부터_나이가_증가한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(100_000L, 161_000L);
        new DownPriceSnapshotMetrics(registry, clock);

        assertThat(registry.get("snapshot.generation.age").gauge().value())
                .isEqualTo(61D);
    }

    @Test
    void 세대_나이는_마지막_관측_후에도_시간에_따라_증가한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(100_000L, 100_000L, 170_001L);
        DownPriceSnapshotMetrics metrics = new DownPriceSnapshotMetrics(registry, clock);

        metrics.recordGenerationAge(Duration.ofSeconds(10));

        assertThat(registry.get("snapshot.generation.age").gauge().value())
                .isEqualTo(80.001D);
    }

    @Test
    void 오래된_세대_관측은_최신_세대_시각을_되돌리지_않는다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(100_000L, 100_000L, 100_000L, 100_000L);
        DownPriceSnapshotMetrics metrics = new DownPriceSnapshotMetrics(registry, clock);

        metrics.recordGenerationAge(Duration.ofSeconds(10));
        metrics.recordGenerationAge(Duration.ofSeconds(30));

        assertThat(registry.get("snapshot.generation.age").gauge().value())
                .isEqualTo(10D);
    }
}
