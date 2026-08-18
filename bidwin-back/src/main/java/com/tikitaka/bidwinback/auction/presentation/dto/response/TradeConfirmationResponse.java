package com.tikitaka.bidwinback.auction.presentation.dto.response;

import com.tikitaka.bidwinback.auction.application.trade.TradeConfirmationResult;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;

public record TradeConfirmationResponse(
        Long tradeId,
        Long auctionId,
        TradeStatus status,
        long finalPrice
) {

    public static TradeConfirmationResponse from(TradeConfirmationResult result) {
        return new TradeConfirmationResponse(
                result.tradeId(),
                result.auctionId(),
                result.status(),
                result.finalPrice()
        );
    }
}
