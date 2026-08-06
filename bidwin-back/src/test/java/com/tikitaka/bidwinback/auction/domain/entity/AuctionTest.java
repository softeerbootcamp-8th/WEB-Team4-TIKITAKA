package com.tikitaka.bidwinback.auction.domain.entity;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.TradeType;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuctionTest {

    @Test
    void 낙찰자_결정_시작부터_밀봉_입찰을_공개한다() {
        assertThat(auction(AuctionStatus.OPEN).isSealedBidRevealed()).isFalse();
        assertThat(auction(AuctionStatus.BID_ONGOING).isSealedBidRevealed()).isFalse();
        assertThat(auction(AuctionStatus.WINNER_DETERMINING).isSealedBidRevealed())
                .isTrue();
        assertThat(auction(AuctionStatus.COMPLETED).isSealedBidRevealed()).isTrue();
        assertThat(auction(AuctionStatus.UNSOLD).isSealedBidRevealed()).isTrue();
    }

    private UpAuction auction(AuctionStatus status) {
        return UpAuction.builder()
                .seller(mock(Member.class))
                .title("밀봉 입찰 공개 정책 테스트")
                .description("경매 상태별 공개 여부 검증")
                .status(status)
                .category(AuctionCategory.HOUSEHOLD)
                .startPrice(100_000L)
                .endedAt(LocalDateTime.of(2026, 8, 6, 13, 0))
                .tradeType(TradeType.DELIVERY)
                .contact("01012345678")
                .buyNowPrice(300_000L)
                .build();
    }
}
