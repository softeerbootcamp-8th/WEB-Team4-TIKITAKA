package com.tikitaka.bidwinback.auction.infrastructure.sse;

import com.tikitaka.bidwinback.auction.application.bid.BidHistoryService;
import com.tikitaka.bidwinback.auction.application.live.AuctionBidCreated;
import com.tikitaka.bidwinback.auction.application.live.AuctionBidHistoryRevealed;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidHistoryItemResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidHistoryResponse;
import com.tikitaka.bidwinback.global.sse.RedisSseEventBus;
import com.tikitaka.bidwinback.global.sse.SseMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionBidSseListenerTest {

    @Mock
    private BidHistoryService bidHistoryService;

    @Mock
    private RedisSseEventBus eventBus;

    private AuctionBidSseListener listener;

    @BeforeEach
    void setUp() {
        listener = new AuctionBidSseListener(bidHistoryService, eventBus);
    }

    @Test
    void 커밋된_공개_입찰은_경매_식별자와_함께_Redis에_발행한다() {
        // given
        BidHistoryItemResponse bid = new BidHistoryItemResponse(
                "BID:9",
                "입**자",
                230_000L,
                1_754_122_920_000L
        );

        // when
        listener.publishBid(new AuctionBidCreated(1L, 9L, bid));

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<SseMessage<AuctionSseMessages.BidCreatedPayload>> message =
                ArgumentCaptor.forClass(SseMessage.class);
        verify(eventBus).publish(message.capture());
        assertThat(message.getValue().data().auctionId()).isEqualTo(1L);
        assertThat(message.getValue().data().entryId()).isEqualTo("BID:9");
        verifyNoInteractions(bidHistoryService);
    }

    @Test
    void 마감되면_밀봉입찰이_포함된_최근내역_snapshot을_발행한다() {
        // given
        BidHistoryResponse history = new BidHistoryResponse(2L, List.of());
        when(bidHistoryService.getBidHistory(1L)).thenReturn(history);

        // when
        listener.publishRevealedHistory(new AuctionBidHistoryRevealed(1L, 4L));

        // then
        verify(eventBus).publish(AuctionSseMessages.bidHistorySnapshot(1L, 4L, history));
    }

    @Test
    void Redis_발행이_실패해도_커밋된_입찰_처리는_실패하지_않는다() {
        // given
        doThrow(new IllegalStateException("redis down")).when(eventBus).publish(any());
        AuctionBidCreated event = new AuctionBidCreated(
                1L,
                9L,
                new BidHistoryItemResponse("BID:9", "입**자", 230_000L, 1L)
        );

        // when & then
        assertThatCode(() -> listener.publishBid(event)).doesNotThrowAnyException();

        verifyNoInteractions(bidHistoryService);
    }
}
