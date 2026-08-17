package com.tikitaka.bidwinback.auction.infrastructure.sse;

import com.tikitaka.bidwinback.auction.application.live.AuctionLiveState;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidHistoryItemResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidHistoryResponse;
import com.tikitaka.bidwinback.global.sse.SseChannel;
import com.tikitaka.bidwinback.global.sse.SseMessage;

import java.util.List;

public final class AuctionSseMessages {

    private static final String NAMESPACE = "auction";
    private static final String STATE_EVENT = "auction-state";
    private static final String STATE_SNAPSHOT_EVENT = "auction-state-snapshot";
    private static final String BID_CREATED_EVENT = "bid-created";
    private static final String BID_HISTORY_SNAPSHOT_EVENT = "bid-history-snapshot";

    //경매를 한번에 내려주기위한 임시 채널
    private static final SseChannel AUCTION_LIST =
            new SseChannel(NAMESPACE, "list-snapshot");


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

    public static SseMessage<List<AuctionLiveState>> auctionList(
            List<AuctionLiveState> states
    ) {
        return new SseMessage<>(
                AUCTION_LIST,
                STATE_SNAPSHOT_EVENT,
                0L,
                List.copyOf(states)
        );
    }

    public static SseMessage<BidCreatedPayload> bidCreated(
            long auctionId,
            long bidId,
            BidHistoryItemResponse bid
    ) {
        return new SseMessage<>(
                channel(auctionId),
                BID_CREATED_EVENT,
                bidId,
                new BidCreatedPayload(
                        auctionId,
                        bid.entryId(),
                        bid.bidder(),
                        bid.amount(),
                        bid.biddedAt()
                )
        );
    }

    public static SseMessage<BidHistoryResponse> bidHistorySnapshot(
            long auctionId,
            long revision,
            BidHistoryResponse history
    ) {
        return new SseMessage<>(
                channel(auctionId),
                BID_HISTORY_SNAPSHOT_EVENT,
                revision,
                history
        );
    }

    public record BidCreatedPayload(
            long auctionId,
            String entryId,
            String bidder,
            long amount,
            long biddedAt
    ) {
    }
}
