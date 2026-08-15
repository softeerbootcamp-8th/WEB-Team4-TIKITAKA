package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.BidHistoryService;
import com.tikitaka.bidwinback.auction.application.live.AuctionLiveState;
import com.tikitaka.bidwinback.auction.application.live.AuctionLiveStateService;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.infrastructure.sse.AuctionSseMessages;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidHistoryResponse;
import com.tikitaka.bidwinback.global.sse.SseChannel;
import com.tikitaka.bidwinback.global.sse.SseHub;
import com.tikitaka.bidwinback.global.sse.SseMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionSseControllerTest {

    @Mock
    private AuctionLiveStateService stateService;
    @Mock
    private BidHistoryService bidHistoryService;
    @Mock
    private SseHub sseHub;
    @Mock
    private SseEmitter emitter;

    private AuctionSseController controller;

    @BeforeEach
    void setUp() {
        controller = new AuctionSseController(stateService, bidHistoryService, sseHub);
    }

    @Test
    void 상세_SSE를_구독하면_버퍼링과_캐시를_막은_event_stream을_응답한다() {
        // given
        when(sseHub.subscribe(
                eq(List.of(AuctionSseMessages.channel(1L))),
                any()
        )).thenReturn(emitter);

        // when
        ResponseEntity<SseEmitter> response = controller.subscribeAuction(1L);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.TEXT_EVENT_STREAM);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst("X-Accel-Buffering")).isEqualTo("no");
        assertThat(response.getBody()).isSameAs(emitter);
    }

    @Test
    void 상세_SSE는_경매_하나짜리_최신_snapshot_공급자를_등록한다() {
        // given
        AuctionLiveState state = state(1L);
        BidHistoryResponse history = new BidHistoryResponse(3L, List.of());
        when(stateService.getState(1L)).thenReturn(state);
        when(bidHistoryService.getBidHistory(1L, state.status(), state.bidCount()))
                .thenReturn(history);
        when(sseHub.subscribe(
                eq(List.of(AuctionSseMessages.channel(1L))),
                any()
        ))
                .thenAnswer(invocation -> {
                    Supplier<? extends Collection<? extends SseMessage<?>>> initialMessages =
                            invocation.getArgument(1);
                    assertThat(initialMessages.get()).isEqualTo(List.of(
                            AuctionSseMessages.state(state),
                            AuctionSseMessages.bidHistorySnapshot(1L, state.revision(), history)
                    ));
                    return emitter;
                });

        // when
        ResponseEntity<SseEmitter> response = controller.subscribeAuction(1L);

        // then
        assertThat(response.getBody()).isSameAs(emitter);
        verify(stateService).getState(1L);
        verify(bidHistoryService).getBidHistory(1L, state.status(), state.bidCount());
    }

    @Test
    void 목록_SSE는_화면이_요청한_ID들의_최신_snapshot_공급자를_등록한다() {
        // given
        List<Long> auctionIds = List.of(1L, 2L);
        List<AuctionLiveState> states = List.of(state(1L), state(2L));
        when(stateService.getStates(auctionIds)).thenReturn(states);
        List<SseChannel> channels = auctionIds.stream()
                .map(AuctionSseMessages::channel)
                .toList();
        when(sseHub.subscribe(eq(channels), any()))
                .thenAnswer(invocation -> {
                    Supplier<? extends Collection<? extends SseMessage<?>>> initialMessages =
                            invocation.getArgument(1);
                    assertThat(initialMessages.get()).isEqualTo(List.of(
                            AuctionSseMessages.state(states.get(0)),
                            AuctionSseMessages.state(states.get(1))
                    ));
                    return emitter;
                });

        // when
        ResponseEntity<SseEmitter> response = controller.subscribeAuctionList(auctionIds);

        // then
        assertThat(response.getBody()).isSameAs(emitter);
        verify(stateService).getStates(auctionIds);
    }

    @Test
    void 목록_SSE는_중복_ID를_제거한_뒤_채널과_snapshot을_같은_목록으로_조회한다() {
        // given
        List<Long> requested = List.of(1L, 1L, 2L, 2L, 1L);
        List<Long> distinct = List.of(1L, 2L);
        List<AuctionLiveState> states = List.of(state(1L), state(2L));
        when(stateService.getStates(distinct)).thenReturn(states);
        List<SseChannel> channels = distinct.stream()
                .map(AuctionSseMessages::channel)
                .toList();
        when(sseHub.subscribe(eq(channels), any()))
                .thenAnswer(invocation -> {
                    Supplier<? extends Collection<? extends SseMessage<?>>> initialMessages =
                            invocation.getArgument(1);
                    assertThat(initialMessages.get()).isEqualTo(List.of(
                            AuctionSseMessages.state(states.get(0)),
                            AuctionSseMessages.state(states.get(1))
                    ));
                    return emitter;
                });

        // when
        ResponseEntity<SseEmitter> response = controller.subscribeAuctionList(requested);

        // then
        assertThat(response.getBody()).isSameAs(emitter);
        verify(stateService).getStates(distinct);
    }

    @Test
    void 잘못된_요청_예외는_협상_실패를_막도록_빈_본문의_400으로_바꾼다() {
        // when
        ResponseEntity<Void> response = controller.handleInvalidRequest();

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNull();
    }

    private AuctionLiveState state(long auctionId) {
        return new AuctionLiveState(
                auctionId,
                1L,
                AuctionType.UP,
                AuctionStatus.BID_ONGOING,
                120_000L,
                3L
        );
    }
}
