package com.tikitaka.bidwinback.auction.application.buynow;

import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;

import java.time.LocalDateTime;

public record BuyNowCommand(
        Long memberId,
        Long auctionId,
        String idempotencyKey,
        long finalPrice,
        LocalDateTime purchasedAt,
        BidStatus bidStatus
) {
}
