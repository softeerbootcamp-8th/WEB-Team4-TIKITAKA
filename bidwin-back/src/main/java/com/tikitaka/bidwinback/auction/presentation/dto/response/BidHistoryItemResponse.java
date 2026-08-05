package com.tikitaka.bidwinback.auction.presentation.dto.response;

public record BidHistoryItemResponse(
        Long id,
        String bidder,
        long amount,
        long biddedAt,
        boolean isMe
) {
}
