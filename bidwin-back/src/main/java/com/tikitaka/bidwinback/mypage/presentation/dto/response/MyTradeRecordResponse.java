package com.tikitaka.bidwinback.mypage.presentation.dto.response;

import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
import com.tikitaka.bidwinback.mypage.domain.enums.TradeRoute;

public record MyTradeRecordResponse(
        long auctionId,
        String title,
        String thumbnailUrl,
        long finalPrice,
        long purchasedAt,
        TradeStatus status,
        TradeRoute route
) {
}
