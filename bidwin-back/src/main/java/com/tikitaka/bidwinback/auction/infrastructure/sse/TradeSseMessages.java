package com.tikitaka.bidwinback.auction.infrastructure.sse;

import com.tikitaka.bidwinback.auction.application.live.TradeLiveState;
import com.tikitaka.bidwinback.global.sse.SseChannel;
import com.tikitaka.bidwinback.global.sse.SseMessage;

public final class TradeSseMessages {

    private static final String NAMESPACE = "trade";
    private static final String STATE_EVENT = "trade-state";

    private TradeSseMessages() {
    }

    public static SseChannel channel(long tradeId) {
        return new SseChannel(NAMESPACE, Long.toString(tradeId));
    }

    /**
     * 거래 상태는 WAITING_CONFIRM→CONFIRMED→COMPLETED로만 전진하므로 enum ordinal이
     * 그대로 단조 증가하는 버전이 된다. 클라이언트는 이 값으로 중복·역순 이벤트를 버린다.
     */
    public static SseMessage<TradeLiveState> state(TradeLiveState state) {
        return new SseMessage<>(
                channel(state.tradeId()),
                STATE_EVENT,
                state.status().ordinal(),
                state
        );
    }
}
