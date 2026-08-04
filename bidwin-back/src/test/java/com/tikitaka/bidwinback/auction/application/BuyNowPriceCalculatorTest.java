package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuyNowPriceCalculatorTest {

    private static final LocalDateTime STARTED_AT =
            LocalDateTime.of(2026, 7, 30, 12, 0);

    private final BuyNowPriceCalculator calculator = new BuyNowPriceCalculator();

    @Test
    void 하락_주기_직전에는_시작가를_유지한다() {
        DownAuction auction = downAuction(
                100_000L,
                40_000L,
                10_000L,
                10L
        );

        long price = calculator.calculate(
                auction,
                STARTED_AT.plusMinutes(9).plusSeconds(59)
        );

        assertThat(price).isEqualTo(100_000L);
    }

    @Test
    void 정확히_하락_주기에_도달하면_한_단계_내린다() {
        DownAuction auction = downAuction(
                100_000L,
                40_000L,
                10_000L,
                10L
        );

        long price = calculator.calculate(
                auction,
                STARTED_AT.plusMinutes(10)
        );

        assertThat(price).isEqualTo(90_000L);
    }

    @Test
    void 하락한_가격이_최저가보다_낮아지면_최저가로_고정한다() {
        DownAuction auction = downAuction(
                100_000L,
                75_000L,
                10_000L,
                10L
        );

        long priceAtFloor = calculator.calculate(
                auction,
                STARTED_AT.plusMinutes(30)
        );
        long priceAfterFloor = calculator.calculate(
                auction,
                STARTED_AT.plusHours(10)
        );

        assertThat(priceAtFloor).isEqualTo(75_000L);
        assertThat(priceAfterFloor).isEqualTo(75_000L);
    }

    @Test
    void 상향_경매는_구매_시각과_관계없이_즉시구매가를_사용한다() {
        UpAuction auction = mock(UpAuction.class);
        when(auction.getBuyNowPrice()).thenReturn(320_000L);

        long price = calculator.calculate(
                auction,
                STARTED_AT.plusDays(1)
        );

        assertThat(price).isEqualTo(320_000L);
    }

    private DownAuction downAuction(
            long startPrice,
            long minimumPrice,
            long dropPrice,
            long priceDropInterval
    ) {
        DownAuction auction = mock(DownAuction.class);
        when(auction.getStartedAt()).thenReturn(STARTED_AT);
        when(auction.getStartPrice()).thenReturn(startPrice);
        when(auction.getMinimumPrice()).thenReturn(minimumPrice);
        when(auction.getDropPrice()).thenReturn(dropPrice);
        when(auction.getPriceDropInterval()).thenReturn(priceDropInterval);
        return auction;
    }
}
