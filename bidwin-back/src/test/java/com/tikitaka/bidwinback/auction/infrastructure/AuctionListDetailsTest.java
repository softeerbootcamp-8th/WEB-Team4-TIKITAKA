package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionListDetailsTest {

    @Test
    void 완료된_경매는_스냅샷_가격_대신_저장된_확정가를_사용한다() {
        AuctionListDetails details = details(AuctionStatus.COMPLETED, 55_000L);

        long currentPrice = details.toRow(
                new AuctionListMetrics(1L, 42_000L, 0L),
                null
        ).currentPrice();

        assertThat(currentPrice).isEqualTo(55_000L);
    }

    @Test
    void 진행중인_경매는_스냅샷_가격을_유지한다() {
        AuctionListDetails details = details(AuctionStatus.BID_ONGOING, 55_000L);

        long currentPrice = details.toRow(
                new AuctionListMetrics(1L, 42_000L, 0L),
                null
        ).currentPrice();

        assertThat(currentPrice).isEqualTo(42_000L);
    }

    private AuctionListDetails details(AuctionStatus status, long currentPrice) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 19, 1, 0);
        return new AuctionListDetails(
                1L,
                1L,
                "하향 경매",
                "판매자",
                AuctionCategory.HOUSEHOLD,
                60_000L,
                now.plusHours(1),
                now.minusHours(1),
                status,
                currentPrice,
                2L,
                now.minusMinutes(10),
                10_000L,
                5_000L,
                5L
        );
    }
}
