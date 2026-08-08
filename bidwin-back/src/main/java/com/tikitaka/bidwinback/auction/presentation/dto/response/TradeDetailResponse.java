package com.tikitaka.bidwinback.auction.presentation.dto.response;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;

/**
 * 거래 화면 단건 조회 응답.
 * sellerContact는 규칙상 CONFIRMED 이후 구매자에게만 채워지고, 그 외에는 항상 null이다.
 */
public record TradeDetailResponse(
        Long tradeId,
        Long auctionId,
        String title,
        String thumbnailUrl,
        AuctionType auctionType,
        TradeStatus status,
        String role,
        long finalPrice,
        long purchasedAt,
        String sellerContact
) {
}
