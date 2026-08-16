package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionClosingBatchProcessorTest {

    private static final int BATCH_SIZE = 3;

    @Mock
    private AuctionClosingService auctionClosingService;

    private AuctionClosingBatchProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AuctionClosingBatchProcessor(auctionClosingService, BATCH_SIZE);
    }

    @Test
    void 배치가_가득_차면_남은_후보를_이어서_처리한다() {
        // given
        when(auctionClosingService.closeBatch(AuctionStatus.OPEN, BATCH_SIZE))
                .thenReturn(BATCH_SIZE, BATCH_SIZE, 1);
        when(auctionClosingService.closeBatch(AuctionStatus.BID_ONGOING, BATCH_SIZE))
                .thenReturn(0);

        // when
        processor.closeEndedAuctions();

        // then
        verify(auctionClosingService, times(3)).closeBatch(AuctionStatus.OPEN, BATCH_SIZE);
    }

    @Test
    void 배치가_가득_차지_않으면_같은_상태를_더_조회하지_않는다() {
        // given
        when(auctionClosingService.closeBatch(AuctionStatus.OPEN, BATCH_SIZE))
                .thenReturn(0);
        when(auctionClosingService.closeBatch(AuctionStatus.BID_ONGOING, BATCH_SIZE))
                .thenReturn(0);

        // when
        processor.closeEndedAuctions();

        // then
        verify(auctionClosingService).closeBatch(AuctionStatus.OPEN, BATCH_SIZE);
        verify(auctionClosingService).closeBatch(AuctionStatus.BID_ONGOING, BATCH_SIZE);
    }

    @Test
    void 한_상태의_후보가_없어도_다른_상태는_계속_처리한다() {
        // given
        when(auctionClosingService.closeBatch(AuctionStatus.OPEN, BATCH_SIZE))
                .thenReturn(0);
        when(auctionClosingService.closeBatch(AuctionStatus.BID_ONGOING, BATCH_SIZE))
                .thenReturn(BATCH_SIZE, 0);

        // when
        processor.closeEndedAuctions();

        // then
        verify(auctionClosingService, times(2))
                .closeBatch(AuctionStatus.BID_ONGOING, BATCH_SIZE);
    }

    @Test
    void 배치가_실패하면_그_상태는_접고_다른_상태를_계속_처리한다() {
        // given
        when(auctionClosingService.closeBatch(AuctionStatus.OPEN, BATCH_SIZE))
                .thenThrow(new IllegalStateException("batch failed"));
        when(auctionClosingService.closeBatch(AuctionStatus.BID_ONGOING, BATCH_SIZE))
                .thenReturn(0);

        // when & then
        assertThatCode(processor::closeEndedAuctions).doesNotThrowAnyException();
        verify(auctionClosingService).closeBatch(AuctionStatus.OPEN, BATCH_SIZE);
        verify(auctionClosingService).closeBatch(AuctionStatus.BID_ONGOING, BATCH_SIZE);
    }

    @Test
    void 후보가_계속_남아도_한_실행에서_처리할_배치_수를_제한한다() {
        // given
        when(auctionClosingService.closeBatch(AuctionStatus.OPEN, BATCH_SIZE))
                .thenReturn(BATCH_SIZE);
        when(auctionClosingService.closeBatch(AuctionStatus.BID_ONGOING, BATCH_SIZE))
                .thenReturn(BATCH_SIZE);

        // when
        processor.closeEndedAuctions();

        // then
        verify(auctionClosingService, times(100)).closeBatch(AuctionStatus.OPEN, BATCH_SIZE);
        verify(auctionClosingService, times(100))
                .closeBatch(AuctionStatus.BID_ONGOING, BATCH_SIZE);
    }

    @Test
    void 배치_크기가_0_이하면_생성에_실패한다() {
        // when & then
        assertThatThrownBy(() -> new AuctionClosingBatchProcessor(auctionClosingService, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
