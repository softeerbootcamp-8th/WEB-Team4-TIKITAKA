package com.tikitaka.bidwinback.auction.infrastructure.sse;

import com.tikitaka.bidwinback.auction.application.live.AuctionLiveState;
import com.tikitaka.bidwinback.global.sse.SseChannel;
import com.tikitaka.bidwinback.global.sse.SseMessage;

public final class AuctionSseMessages {

    private static final String NAMESPACE = "auction";
    private static final String STATE_EVENT = "auction-state";

    private AuctionSseMessages() {
    }

    public static SseChannel channel(long auctionId) {
        return new SseChannel(NAMESPACE, Long.toString(auctionId));
    }

    public static SseMessage<AuctionLiveState> state(AuctionLiveState state) {
        return new SseMessage<>(
                channel(state.auctionId()),
                STATE_EVENT,
                state.revision(),
                state
        );
    }
}
