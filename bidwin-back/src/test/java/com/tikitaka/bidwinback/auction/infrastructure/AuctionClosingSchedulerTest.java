package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.AuctionClosingService;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionClosingSchedulerTest {

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private AuctionClosingService auctionClosingService;

    private AuctionClosingScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AuctionClosingScheduler(
                auctionRepository,
                auctionClosingService
        );
    }

    @Test
    void 조회된_마감_대상을_종료시각_순서대로_처리한다() {
        // given
        when(auctionRepository.findClosingCandidateIds())
                .thenReturn(List.of(1L, 2L));

        // when
        scheduler.closeEndedAuctions();

        // then
        InOrder inOrder = inOrder(auctionClosingService);
        inOrder.verify(auctionClosingService).closeIfAvailable(1L);
        inOrder.verify(auctionClosingService).closeIfAvailable(2L);
    }

    @Test
    void 한_경매의_마감이_실패해도_나머지_경매를_계속_처리한다() {
        // given
        when(auctionRepository.findClosingCandidateIds())
                .thenReturn(List.of(1L, 2L));
        when(auctionClosingService.closeIfAvailable(1L))
                .thenThrow(new IllegalStateException("settlement failed"));

        // when & then
        assertThatCode(scheduler::closeEndedAuctions).doesNotThrowAnyException();
        verify(auctionClosingService).closeIfAvailable(2L);
    }
}
