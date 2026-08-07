package com.tikitaka.bidwinback.auction.infrastructure.sse;

import com.tikitaka.bidwinback.auction.application.BidHistoryService;
import com.tikitaka.bidwinback.auction.application.live.AuctionBidCreated;
import com.tikitaka.bidwinback.auction.application.live.AuctionBidHistoryRevealed;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidHistoryItemResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidHistoryResponse;
import com.tikitaka.bidwinback.global.sse.SseHub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class AuctionBidSseListenerTest {

    @Mock
    private BidHistoryService bidHistoryService;

    @Mock
    private SseHub sseHub;

    private AuctionBidSseListener listener;

    @BeforeEach
    void setUp() {
        listener = new AuctionBidSseListener(bidHistoryService, sseHub);
    }

    @Test
    void 구독자가_있으면_커밋된_입찰_한_건을_발행한다() {
        BidHistoryItemResponse bid = new BidHistoryItemResponse(
                "BID:9",
                "입**자",
                230_000L,
                1_754_122_920_000L
        );
        when(sseHub.hasSubscribers(AuctionSseMessages.channel(1L))).thenReturn(true);
        when(bidHistoryService.getPublishedBid(1L, 9L)).thenReturn(bid);

        listener.publishBid(new AuctionBidCreated(1L, 9L));

        verify(sseHub).publish(AuctionSseMessages.bidCreated(1L, 9L, bid));
    }

    @Test
    void 마감되면_밀봉입찰이_포함된_최근내역_snapshot을_발행한다() {
        BidHistoryResponse history = new BidHistoryResponse(2L, List.of());
        when(sseHub.hasSubscribers(AuctionSseMessages.channel(1L))).thenReturn(true);
        when(bidHistoryService.getBidHistory(1L)).thenReturn(history);

        listener.publishRevealedHistory(new AuctionBidHistoryRevealed(1L, 4L));

        verify(sseHub).publish(AuctionSseMessages.bidHistorySnapshot(1L, 4L, history));
    }

    @Test
    void 구독자가_없으면_입찰내역을_조회하지_않는다() {
        when(sseHub.hasSubscribers(AuctionSseMessages.channel(1L))).thenReturn(false);

        listener.publishBid(new AuctionBidCreated(1L, 9L));

        verifyNoInteractions(bidHistoryService);
        verify(sseHub, never()).publish(any());
    }
}
