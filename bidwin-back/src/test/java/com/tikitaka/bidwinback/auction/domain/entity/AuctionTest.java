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

    @Test
    void 낙찰_처리는_revision을_올려_완료_상태를_SSE로_전파하게_한다() {
        UpAuction auction = auction(AuctionStatus.BID_ONGOING);
        long before = auction.getRevision();

        auction.complete(200_000L, LocalDateTime.of(2026, 8, 6, 13, 0));

        assertThat(auction.getRevision()).isEqualTo(before + 1);
    }

    @Test
    void 유찰_처리는_revision을_올려_유찰_상태를_SSE로_전파하게_한다() {
        UpAuction auction = auction(AuctionStatus.OPEN);
        long before = auction.getRevision();

        auction.markUnsold(LocalDateTime.of(2026, 8, 6, 13, 0));

        assertThat(auction.getRevision()).isEqualTo(before + 1);
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
