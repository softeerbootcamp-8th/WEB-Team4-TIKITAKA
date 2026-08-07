package com.tikitaka.bidwinback.auction.presentation.dto.response;

public record BidHistoryItemResponse(
        String entryId,
        String bidder,
        long amount,
        long biddedAt
) {
}
