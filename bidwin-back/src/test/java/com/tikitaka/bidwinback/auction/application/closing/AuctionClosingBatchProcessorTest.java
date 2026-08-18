package com.tikitaka.bidwinback.auction.application.closing;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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
    void 후보가_남아_있는_동안_이어서_처리한다() {
        // given
        when(auctionClosingService.closeBatch(AuctionStatus.OPEN, BATCH_SIZE))
                .thenReturn(BATCH_SIZE, BATCH_SIZE, 0);
        when(auctionClosingService.closeBatch(AuctionStatus.BID_ONGOING, BATCH_SIZE))
                .thenReturn(0);

        // when
        processor.closeEndedAuctions();

        // then
        verify(auctionClosingService, times(3)).closeBatch(AuctionStatus.OPEN, BATCH_SIZE);
    }

    @Test
    void 다른_서버가_후보를_쥐어_배치가_짧게_잡혀도_같은_상태를_이어서_처리한다() {
        // given
        when(auctionClosingService.closeBatch(AuctionStatus.OPEN, BATCH_SIZE))
                .thenReturn(BATCH_SIZE - 1, 1, 0);
        when(auctionClosingService.closeBatch(AuctionStatus.BID_ONGOING, BATCH_SIZE))
                .thenReturn(0);

        // when
        processor.closeEndedAuctions();

        // then
        verify(auctionClosingService, times(3)).closeBatch(AuctionStatus.OPEN, BATCH_SIZE);
    }

    @Test
    void 후보를_한_건도_선점하지_못하면_같은_상태를_더_조회하지_않는다() {
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
    void 배치가_실패하면_후보를_개별_처리하고_실패한_후보만_넘긴다() {
        // given
        when(auctionClosingService.closeBatch(AuctionStatus.OPEN, BATCH_SIZE))
                .thenReturn(BATCH_SIZE)
                .thenThrow(new IllegalStateException("batch failed"));
        when(auctionClosingService.findClosingCandidateIds(
                AuctionStatus.OPEN,
                BATCH_SIZE * 99
        )).thenReturn(List.of(1L, 2L, 3L));
        when(auctionClosingService.closeOne(AuctionStatus.OPEN, 1L)).thenReturn(1);
        when(auctionClosingService.closeOne(AuctionStatus.OPEN, 2L))
                .thenThrow(new IllegalStateException("auction failed"));
        when(auctionClosingService.closeOne(AuctionStatus.OPEN, 3L)).thenReturn(1);
        when(auctionClosingService.closeBatch(AuctionStatus.BID_ONGOING, BATCH_SIZE))
                .thenReturn(0);

        // when & then
        assertThatCode(processor::closeEndedAuctions).doesNotThrowAnyException();
        verify(auctionClosingService, times(2)).closeBatch(AuctionStatus.OPEN, BATCH_SIZE);
        verify(auctionClosingService).closeOne(AuctionStatus.OPEN, 1L);
        verify(auctionClosingService).closeOne(AuctionStatus.OPEN, 2L);
        verify(auctionClosingService).closeOne(AuctionStatus.OPEN, 3L);
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
    void 개별_재처리_후보_수_계산이_넘쳐도_다른_상태를_계속_처리한다() {
        // given
        int overflowBatchSize = Integer.MAX_VALUE;
        processor = new AuctionClosingBatchProcessor(
                auctionClosingService,
                overflowBatchSize
        );
        when(auctionClosingService.closeBatch(AuctionStatus.OPEN, overflowBatchSize))
                .thenThrow(new IllegalStateException("batch failed"));
        when(auctionClosingService.closeBatch(
                AuctionStatus.BID_ONGOING,
                overflowBatchSize
        )).thenReturn(0);

        // when & then
        assertThatCode(processor::closeEndedAuctions).doesNotThrowAnyException();
        verify(auctionClosingService).closeBatch(
                AuctionStatus.BID_ONGOING,
                overflowBatchSize
        );
    }

    @Test
    void 배치_크기가_0_이하면_생성에_실패한다() {
        // when & then
        assertThatThrownBy(() -> new AuctionClosingBatchProcessor(auctionClosingService, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
