package com.tikitaka.bidwinback.auction.application.trade;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;

public record TradeConfirmationResult(
        Long tradeId,
        Long auctionId,
        TradeStatus status,
        long finalPrice
) {

    public static TradeConfirmationResult from(AuctionTrade trade) {
        return new TradeConfirmationResult(
                trade.getId(),
                trade.getAuction().getId(),
                trade.getStatus(),
                trade.getFinalPrice()
        );
    }
}
