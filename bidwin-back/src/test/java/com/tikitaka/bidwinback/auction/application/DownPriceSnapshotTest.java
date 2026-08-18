package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DownPriceSnapshotTest {

    @Test
    void 정렬별_목록을_불변_복사하고_요청한_정렬을_반환한다() {
        List<AuctionPriceSnapshot> low = new java.util.ArrayList<>(List.of(snapshot(1L)));
        List<AuctionPriceSnapshot> high = new java.util.ArrayList<>(List.of(snapshot(2L)));

        DownPriceSnapshot snapshot = new DownPriceSnapshot(
                LocalDateTime.of(2026, 8, 18, 12, 0),
                low,
                high
        );
        low.clear();
        high.clear();

        assertThat(snapshot.entries(AuctionSort.PRICE_LOW)).containsExactly(snapshot(1L));
        assertThat(snapshot.entries(AuctionSort.PRICE_HIGH)).containsExactly(snapshot(2L));
    }

    @Test
    void LOW_HIGH_크기가_다르면_세대를_만들지_않는다() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DownPriceSnapshot(
                LocalDateTime.of(2026, 8, 18, 12, 0),
                List.of(snapshot(1L)),
                List.of()
        ));
    }

    private AuctionPriceSnapshot snapshot(long auctionId) {
        return new AuctionPriceSnapshot(auctionId, auctionId * 100, auctionId * 100);
    }
}
