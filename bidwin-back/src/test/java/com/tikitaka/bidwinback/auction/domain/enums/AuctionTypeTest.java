package com.tikitaka.bidwinback.auction.domain.enums;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;

class AuctionTypeTest {

    @Test
    void 상향_경매는_UP_타입이다() {
        // given
        Auction auction = mock(UpAuction.class);

        // when
        AuctionType type = AuctionType.from(auction);

        // then
        assertThat(type).isEqualTo(AuctionType.UP);
    }

    @Test
    void 하향_경매는_DOWN_타입이다() {
        // given
        Auction auction = mock(DownAuction.class);

        // when
        AuctionType type = AuctionType.from(auction);

        // then
        assertThat(type).isEqualTo(AuctionType.DOWN);
    }

    @Test
    void 지원하지_않는_경매_구현체는_타입을_반환하지_않는다() {
        // given
        Auction auction = mock(Auction.class);

        // when
        Throwable thrown = catchThrowable(() -> AuctionType.from(auction));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }
}
