package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.application.live.AuctionBidHistoryRevealed;
import com.tikitaka.bidwinback.auction.application.live.AuctionStateChanged;
import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionClosingServiceTest {

    private static final long AUCTION_ID = 42L;
    private static final LocalDateTime DATABASE_TIME =
            LocalDateTime.of(2026, 8, 7, 12, 0);

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private UpAuctionSettlementService upAuctionSettlementService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Auction auction;

    @Mock
    private UpAuction upAuction;

    @InjectMocks
    private AuctionClosingService auctionClosingService;

    @Test
    void 마감된_OPEN_경매는_유찰한다() {
        // given
        stubLockedAuction(AuctionStatus.OPEN);
        when(auctionRepository.currentDatabaseTime()).thenReturn(DATABASE_TIME);

        // when
        boolean closed = auctionClosingService.closeOneCandidate();

        // then
        assertThat(closed).isTrue();
        verify(auction).markUnsold(DATABASE_TIME);
        verify(eventPublisher).publishEvent(new AuctionStateChanged(AUCTION_ID));
        verifyNoInteractions(upAuctionSettlementService);
    }

    @Test
    void OPEN_경매가_유찰되면_최종_상태_변경_이벤트를_발행한다() {
        // given
        stubLockedAuction(AuctionStatus.OPEN);
        when(auctionRepository.currentDatabaseTime()).thenReturn(DATABASE_TIME);

        // when
        auctionClosingService.closeOneCandidate();

        // then
        verify(eventPublisher).publishEvent(new AuctionStateChanged(AUCTION_ID));
    }

    @Test
    void 마감된_BID_ONGOING_경매는_낙찰자를_판정한다() {
        // given
        stubLockedAuction(upAuction, AuctionStatus.BID_ONGOING);
        when(upAuction.getId()).thenReturn(AUCTION_ID);
        when(upAuction.getRevision()).thenReturn(8L);

        // when
        boolean closed = auctionClosingService.closeOneCandidate();

        // then
        assertThat(closed).isTrue();
        verify(upAuctionSettlementService).settle(AUCTION_ID);
        verify(upAuction, never()).markUnsold(any());
        verify(eventPublisher).publishEvent(new AuctionBidHistoryRevealed(AUCTION_ID, 8L));
    }

    @Test
    void 다른_작업이_경매를_선점했다면_상태를_바꾸지_않는다() {
        // given
        when(auctionRepository.findOneClosingCandidateIdForUpdateSkipLocked())
                .thenReturn(Optional.empty());

        // when
        boolean closed = auctionClosingService.closeOneCandidate();

        // then
        assertThat(closed).isFalse();
        verify(auctionRepository, never()).findById(AUCTION_ID);
        verifyNoInteractions(auction, upAuction, upAuctionSettlementService, eventPublisher);
    }

    private void stubLockedAuction(AuctionStatus status) {
        stubLockedAuction(auction, status);
    }

    private void stubLockedAuction(Auction lockedAuction, AuctionStatus status) {
        when(auctionRepository.findOneClosingCandidateIdForUpdateSkipLocked())
                .thenReturn(Optional.of(AUCTION_ID));
        when(auctionRepository.findById(AUCTION_ID)).thenReturn(Optional.of(lockedAuction));
        when(lockedAuction.getStatus()).thenReturn(status);
    }
}
