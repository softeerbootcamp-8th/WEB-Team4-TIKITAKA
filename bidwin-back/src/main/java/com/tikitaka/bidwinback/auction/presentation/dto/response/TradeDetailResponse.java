package com.tikitaka.bidwinback.auction.presentation.dto.response;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
import io.swagger.v3.oas.annotations.media.Schema;

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
        @Schema(description = "현재 회원의 거래 역할", example = "BUYER", allowableValues = {"BUYER", "SELLER"})
        String role,
        long finalPrice,
        @Schema(description = "구매 확정 시각(epoch milliseconds)", example = "1786860000000")
        long purchasedAt,
        @Schema(description = "판매자 연락처. 구매자에게 CONFIRMED 이후에만 제공")
        String sellerContact
) {
}
