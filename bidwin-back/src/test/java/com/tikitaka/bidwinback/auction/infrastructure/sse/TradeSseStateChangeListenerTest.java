package com.tikitaka.bidwinback.auction.infrastructure.sse;

import com.tikitaka.bidwinback.auction.application.live.TradeLiveState;
import com.tikitaka.bidwinback.auction.application.live.TradeStatusChanged;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
import com.tikitaka.bidwinback.global.sse.SseHub;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeSseStateChangeListenerTest {

    @Test
    void 커밋된_거래_상태는_이벤트의_snapshot을_그대로_발행한다() {
        // given
        SseHub sseHub = mock(SseHub.class);
        TradeSseStateChangeListener listener = new TradeSseStateChangeListener(sseHub);
        TradeLiveState state = new TradeLiveState(7L, 42L, TradeStatus.CONFIRMED);
        when(sseHub.hasSubscribers(TradeSseMessages.channel(7L))).thenReturn(true);

        // when
        listener.publishCommittedState(new TradeStatusChanged(state));

        // then
        verify(sseHub).publish(TradeSseMessages.state(state));
    }
}
