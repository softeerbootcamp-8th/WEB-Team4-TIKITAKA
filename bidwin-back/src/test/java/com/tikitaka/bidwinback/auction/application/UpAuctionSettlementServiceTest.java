package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.application.live.AuctionStateChanged;
import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.entity.Bid;
import com.tikitaka.bidwinback.auction.domain.entity.SealedBid;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;
import com.tikitaka.bidwinback.auction.domain.exception.SettlementException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionTradeRepository;
import com.tikitaka.bidwinback.auction.domain.repository.BidRepository;
import com.tikitaka.bidwinback.auction.domain.repository.SealedBidRepository;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.SETTLEMENT_NOT_AVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpAuctionSettlementServiceTest {

    private static final long AUCTION_ID = 42L;
    private static final LocalDateTime ENDED_AT =
            LocalDateTime.of(2026, 8, 5, 12, 0);

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private SealedBidRepository sealedBidRepository;

    @Mock
    private AuctionTradeRepository auctionTradeRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UpAuction auction;

    @Mock
    private Bid openBid;

    @Mock
    private SealedBid sealedBid;

    @Mock
    private Member openBidder;

    @Mock
    private Member sealedBidder;

    @InjectMocks
    private UpAuctionSettlementService settlementService;

    @Test
    void 일반입찰보다_높은_밀봉입찰자를_낙찰자로_확정한다() {
        // given
        stubOngoingEndedAuction();
        when(bidRepository.findWinnerByAuctionIdAndStatus(AUCTION_ID, BidStatus.UP))
                .thenReturn(Optional.of(openBid));
        when(sealedBidRepository.findWinnerByAuctionId(AUCTION_ID))
                .thenReturn(Optional.of(sealedBid));
        when(sealedBidRepository.countByAuctionId(AUCTION_ID)).thenReturn(1L);
        when(openBid.getBidder()).thenReturn(openBidder);
        when(openBid.getPrice()).thenReturn(200_000L);
        when(sealedBid.getBidder()).thenReturn(sealedBidder);
        when(sealedBid.getPrice()).thenReturn(230_000L);
        when(sealedBidder.getId()).thenReturn(7L);
        when(auctionTradeRepository.save(any(AuctionTrade.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UpAuctionSettlementResult result = settlementService.settle(auction);

        // then
        ArgumentCaptor<AuctionTrade> tradeCaptor = ArgumentCaptor.forClass(AuctionTrade.class);
        verify(auctionTradeRepository).save(tradeCaptor.capture());
        AuctionTrade trade = tradeCaptor.getValue();
        assertAll(
                () -> assertThat(trade.getBuyer()).isSameAs(sealedBidder),
                () -> assertThat(trade.getFinalPrice()).isEqualTo(230_000L),
                () -> assertThat(result.status()).isEqualTo(AuctionStatus.COMPLETED),
                () -> assertThat(result.winnerId()).isEqualTo(7L),
                () -> assertThat(result.finalPrice()).isEqualTo(230_000L)
        );
        verify(auction).complete(230_000L, ENDED_AT, 1L);
        verify(sealedBidRepository).countByAuctionId(AUCTION_ID);
    }

    @Test
    void 입찰이_없으면_유찰_처리한다() {
        // given
        stubOngoingEndedAuction();
        when(bidRepository.findWinnerByAuctionIdAndStatus(AUCTION_ID, BidStatus.UP))
                .thenReturn(Optional.empty());
        when(sealedBidRepository.findWinnerByAuctionId(AUCTION_ID))
                .thenReturn(Optional.empty());
        when(sealedBidRepository.countByAuctionId(AUCTION_ID)).thenReturn(0L);

        // when
        UpAuctionSettlementResult result = settlementService.settle(auction);

        // then
        assertThat(result.status()).isEqualTo(AuctionStatus.UNSOLD);
        verify(auction).markUnsold(ENDED_AT, 0L);
        verify(sealedBidRepository).countByAuctionId(AUCTION_ID);
        verifyNoInteractions(auctionTradeRepository);
    }

    @Test
    void 낙찰이_확정되면_최종_상태_변경_이벤트를_발행한다() {
        // given
        stubOngoingEndedAuction();
        when(bidRepository.findWinnerByAuctionIdAndStatus(AUCTION_ID, BidStatus.UP))
                .thenReturn(Optional.of(openBid));
        when(sealedBidRepository.findWinnerByAuctionId(AUCTION_ID))
                .thenReturn(Optional.empty());
        when(openBid.getBidder()).thenReturn(openBidder);
        when(openBid.getPrice()).thenReturn(200_000L);
        when(openBidder.getId()).thenReturn(7L);
        when(auctionTradeRepository.save(any(AuctionTrade.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        settlementService.settle(auction);

        // then
        verify(eventPublisher).publishEvent(new AuctionStateChanged(AUCTION_ID));
    }

    @Test
    void 입찰_없이_유찰되면_최종_상태_변경_이벤트를_발행한다() {
        // given
        stubOngoingEndedAuction();
        when(bidRepository.findWinnerByAuctionIdAndStatus(AUCTION_ID, BidStatus.UP))
                .thenReturn(Optional.empty());
        when(sealedBidRepository.findWinnerByAuctionId(AUCTION_ID))
                .thenReturn(Optional.empty());

        // when
        settlementService.settle(auction);

        // then
        verify(eventPublisher).publishEvent(new AuctionStateChanged(AUCTION_ID));
    }

    @Test
    void 이미_완료된_경매는_거래를_반환하고_이벤트를_중복_발행하지_않는다() {
        // given
        Member winner = sealedBidder;
        AuctionTrade trade = AuctionTrade.builder()
                .auction(auction)
                .buyer(winner)
                .finalPrice(230_000L)
                .purchasedAt(ENDED_AT)
                .build();
        when(auction.getStatus()).thenReturn(AuctionStatus.COMPLETED);
        when(auction.getId()).thenReturn(AUCTION_ID);
        when(winner.getId()).thenReturn(7L);
        when(auctionTradeRepository.findByAuctionId(AUCTION_ID))
                .thenReturn(Optional.of(trade));

        // when
        UpAuctionSettlementResult result = settlementService.settle(auction);

        // then
        assertThat(result.winnerId()).isEqualTo(7L);
        verifyNoInteractions(bidRepository, sealedBidRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void 종료_전에는_정산할_수_없다() {
        // given
        when(auction.getStatus()).thenReturn(AuctionStatus.BID_ONGOING);
        when(auction.getEndedAt()).thenReturn(ENDED_AT.plusSeconds(1));
        when(auctionRepository.currentDatabaseTime()).thenReturn(ENDED_AT);

        // when
        SettlementException exception = assertThrows(
                SettlementException.class,
                () -> settlementService.settle(auction)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(SETTLEMENT_NOT_AVAILABLE);
        verifyNoInteractions(bidRepository, sealedBidRepository, auctionTradeRepository);
    }

    private void stubOngoingEndedAuction() {
        when(auction.getId()).thenReturn(AUCTION_ID);
        when(auction.getStatus()).thenReturn(AuctionStatus.BID_ONGOING);
        when(auction.getEndedAt()).thenReturn(ENDED_AT);
        when(auctionRepository.currentDatabaseTime()).thenReturn(ENDED_AT);
    }
}
