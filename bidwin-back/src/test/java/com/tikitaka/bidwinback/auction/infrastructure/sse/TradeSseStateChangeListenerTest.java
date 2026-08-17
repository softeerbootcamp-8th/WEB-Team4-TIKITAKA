package com.tikitaka.bidwinback.auction.infrastructure.sse;

import com.tikitaka.bidwinback.auction.application.live.TradeLiveState;
import com.tikitaka.bidwinback.auction.application.live.TradeStatusChanged;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
import com.tikitaka.bidwinback.global.sse.RedisSseEventBus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TradeSseStateChangeListenerTest {

    @Test
    void 커밋된_거래_상태는_이벤트의_snapshot을_그대로_발행한다() {
        // given
        RedisSseEventBus eventBus = mock(RedisSseEventBus.class);
        TradeSseStateChangeListener listener = new TradeSseStateChangeListener(eventBus);
        TradeLiveState state = new TradeLiveState(7L, 42L, TradeStatus.CONFIRMED);

        // when
        listener.publishCommittedState(new TradeStatusChanged(state));

        // then
        verify(eventBus).publish(TradeSseMessages.state(state));
    }

    @Test
    void Redis_발행이_실패해도_커밋된_거래_처리는_실패하지_않는다() {
        // given
        RedisSseEventBus eventBus = mock(RedisSseEventBus.class);
        TradeSseStateChangeListener listener = new TradeSseStateChangeListener(eventBus);
        TradeLiveState state = new TradeLiveState(7L, 42L, TradeStatus.CONFIRMED);
        doThrow(new IllegalStateException("redis down"))
                .when(eventBus).publish(TradeSseMessages.state(state));

        // when & then
        assertThatCode(() -> listener.publishCommittedState(new TradeStatusChanged(state)))
                .doesNotThrowAnyException();
    }
}
