package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;

import java.time.LocalDateTime;

public record BuyNowResult(
        Long tradeId,
        Long auctionId,
        long finalPrice,
        LocalDateTime purchasedAt
) {

    public static BuyNowResult from(AuctionTrade trade) {
        return new BuyNowResult(
                trade.getId(),
                trade.getAuction().getId(),
                trade.getFinalPrice(),
                trade.getPurchasedAt()
        );
    }
}
