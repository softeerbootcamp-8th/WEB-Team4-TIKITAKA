package com.tikitaka.bidwinback.auction.application;

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
    void 한_배치에서_마감_후보를_100번_처리한다() {
        // given
        when(auctionClosingService.closeOneCandidate()).thenReturn(true);

        // when
        processor.closeEndedAuctions();

        // then
        verify(auctionClosingService, times(100)).closeOneCandidate();
    }

    @Test
    void 마감할_후보가_없으면_배치를_즉시_끝낸다() {
        // given
        when(auctionClosingService.closeOneCandidate()).thenReturn(false);

        // when
        processor.closeEndedAuctions();

        // then
        verify(auctionClosingService).closeOneCandidate();
    }

    @Test
    void 한_경매의_마감이_실패해도_100번까지_계속_처리한다() {
        // given
        when(auctionClosingService.closeOneCandidate())
                .thenThrow(new IllegalStateException("settlement failed"))
                .thenReturn(true);

        // when & then
        assertThatCode(processor::closeEndedAuctions).doesNotThrowAnyException();
        verify(auctionClosingService, times(100)).closeOneCandidate();
    }
}
