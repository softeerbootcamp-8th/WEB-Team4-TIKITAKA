package com.tikitaka.bidwinback.auction.application;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DownPriceSnapshotBuildKeyTest {

    @Test
    void DB_현재시각을_30초_세대_슬롯으로_내린다() {
        assertThat(DownPriceSnapshotBuildKey.latestSlot(
                LocalDateTime.of(2026, 8, 18, 12, 34, 29, 999_999_000)
        ).generationAt()).isEqualTo(LocalDateTime.of(2026, 8, 18, 12, 34, 0));

        assertThat(DownPriceSnapshotBuildKey.latestSlot(
                LocalDateTime.of(2026, 8, 18, 12, 34, 30, 123_000_000)
        ).generationAt()).isEqualTo(LocalDateTime.of(2026, 8, 18, 12, 34, 30));
    }

    @Test
    void 서버가_발급할_수_있는_세대_시각인지_판별한다() {
        assertThat(DownPriceSnapshotBuildKey.isGenerationSlot(
                LocalDateTime.of(2026, 8, 18, 12, 34, 30)
        )).isTrue();
        assertThat(DownPriceSnapshotBuildKey.isGenerationSlot(
                LocalDateTime.of(2026, 8, 18, 12, 34, 31)
        )).isFalse();
    }
}
