package com.tikitaka.bidwinback.auction.presentation.dto.response;

import com.tikitaka.bidwinback.auction.application.BuyNowResult;

import java.time.LocalDateTime;

public record BuyNowResponse(
        Long tradeId,
        Long auctionId,
        long finalPrice,
        LocalDateTime purchasedAt
) {

    public static BuyNowResponse from(BuyNowResult result) {
        return new BuyNowResponse(
                result.tradeId(),
                result.auctionId(),
                result.finalPrice(),
                result.purchasedAt()
        );
    }
}
