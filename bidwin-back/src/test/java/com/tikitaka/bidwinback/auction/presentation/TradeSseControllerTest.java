package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.TradeQueryService;
import com.tikitaka.bidwinback.auction.application.live.TradeLiveState;
import com.tikitaka.bidwinback.auction.application.live.TradeLiveStateService;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
import com.tikitaka.bidwinback.auction.domain.exception.TradeException;
import com.tikitaka.bidwinback.auction.infrastructure.sse.TradeSseMessages;
import com.tikitaka.bidwinback.global.auth.AuthMember;
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

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.TRADE_ACCESS_DENIED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeSseControllerTest {

    private static final long MEMBER_ID = 1L;
    private static final long TRADE_ID = 7L;
    private static final long AUCTION_ID = 42L;

    @Mock
    private TradeQueryService tradeQueryService;
    @Mock
    private TradeLiveStateService tradeLiveStateService;
    @Mock
    private SseHub sseHub;
    @Mock
    private SseEmitter emitter;

    private TradeSseController controller;
    private AuthMember authMember;

    @BeforeEach
    void setUp() {
        controller = new TradeSseController(
                tradeQueryService,
                tradeLiveStateService,
                sseHub
        );
        authMember = new AuthMember(MEMBER_ID, 0L, Instant.EPOCH);
    }

    @Test
    void 거래_참여자가_SSE를_구독하면_최신_상태를_event_stream으로_응답한다() {
        // given
        TradeLiveState state = new TradeLiveState(
                TRADE_ID,
                AUCTION_ID,
                TradeStatus.CONFIRMED
        );
        when(tradeLiveStateService.getState(TRADE_ID)).thenReturn(state);
        when(sseHub.subscribe(
                eq(List.of(TradeSseMessages.channel(TRADE_ID))),
                any()
        )).thenAnswer(invocation -> {
            Supplier<? extends Collection<? extends SseMessage<?>>> initialMessages =
                    invocation.getArgument(1);
            assertThat(initialMessages.get()).isEqualTo(List.of(TradeSseMessages.state(state)));
            return emitter;
        });

        // when
        ResponseEntity<SseEmitter> response = controller.subscribeTrade(authMember, TRADE_ID);

        // then
        verify(tradeQueryService).verifyParticipant(MEMBER_ID, TRADE_ID);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst("X-Accel-Buffering")).isEqualTo("no");
        assertThat(response.getBody()).isSameAs(emitter);
    }

    @Test
    void 거래_참여자가_아니면_SSE_연결을_등록하지_않는다() {
        // given
        TradeException exception = new TradeException(TRADE_ACCESS_DENIED);
        doThrow(exception)
                .when(tradeQueryService)
                .verifyParticipant(MEMBER_ID, TRADE_ID);

        // when & then
        assertThatThrownBy(() -> controller.subscribeTrade(authMember, TRADE_ID))
                .isSameAs(exception);
        verifyNoInteractions(tradeLiveStateService, sseHub);
    }
}
