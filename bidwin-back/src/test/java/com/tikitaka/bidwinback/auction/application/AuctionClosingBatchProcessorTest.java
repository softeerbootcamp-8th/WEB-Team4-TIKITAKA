package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionClosingBatchProcessorTest {

    @Mock
    private AuctionClosingService auctionClosingService;

    private AuctionClosingBatchProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AuctionClosingBatchProcessor(auctionClosingService);
    }

    @Test
    void 한_배치에서_상태별로_마감_후보를_100번씩_처리한다() {
        // given
        when(auctionClosingService.closeOneCandidate(AuctionStatus.OPEN))
                .thenReturn(true);
        when(auctionClosingService.closeOneCandidate(AuctionStatus.BID_ONGOING))
                .thenReturn(true);

        // when
        processor.closeEndedAuctions();

        // then
        verify(auctionClosingService, times(100))
                .closeOneCandidate(AuctionStatus.OPEN);
        verify(auctionClosingService, times(100))
                .closeOneCandidate(AuctionStatus.BID_ONGOING);
    }

    @Test
    void 한_상태의_후보가_없어도_다른_상태는_계속_처리한다() {
        // given
        when(auctionClosingService.closeOneCandidate(AuctionStatus.OPEN))
                .thenReturn(false);
        when(auctionClosingService.closeOneCandidate(AuctionStatus.BID_ONGOING))
                .thenReturn(true);

        // when
        processor.closeEndedAuctions();

        // then
        verify(auctionClosingService).closeOneCandidate(AuctionStatus.OPEN);
        verify(auctionClosingService, times(100))
                .closeOneCandidate(AuctionStatus.BID_ONGOING);
    }

    @Test
    void 한_경매의_마감이_실패해도_해당_상태를_100번까지_계속_처리한다() {
        // given
        when(auctionClosingService.closeOneCandidate(AuctionStatus.OPEN))
                .thenThrow(new IllegalStateException("settlement failed"))
                .thenReturn(true);
        when(auctionClosingService.closeOneCandidate(AuctionStatus.BID_ONGOING))
                .thenReturn(false);

        // when & then
        assertThatCode(processor::closeEndedAuctions).doesNotThrowAnyException();
        verify(auctionClosingService, times(100))
                .closeOneCandidate(AuctionStatus.OPEN);
    }
}
