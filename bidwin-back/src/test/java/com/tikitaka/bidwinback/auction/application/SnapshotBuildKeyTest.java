package com.tikitaka.bidwinback.auction.application;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotBuildKeyTest {

    @Test
    void DB_현재시각을_30초_세대_슬롯으로_내린다() {
        assertThat(SnapshotBuildKey.latestSlot(
                LocalDateTime.of(2026, 8, 18, 12, 34, 29, 999_999_000)
        ).generationAt()).isEqualTo(LocalDateTime.of(2026, 8, 18, 12, 34, 0));

        assertThat(SnapshotBuildKey.latestSlot(
                LocalDateTime.of(2026, 8, 18, 12, 34, 30, 123_000_000)
        ).generationAt()).isEqualTo(LocalDateTime.of(2026, 8, 18, 12, 34, 30));
    }
}
