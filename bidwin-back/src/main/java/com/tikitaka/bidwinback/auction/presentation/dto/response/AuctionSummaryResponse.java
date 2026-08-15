package com.tikitaka.bidwinback.auction.presentation.dto.response;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;

public record AuctionSummaryResponse(
        Long auctionId,
        AuctionType auctionType,
        String title,
        String sellerName,
        AuctionCategory category,
        // 업로드된 이미지가 없으면 null. 프론트가 자리표시자를 그린다.
        String thumbnailUrl,
        // 진행 중 경매의 목록 가격. COMPLETED는 asOf와 무관하게 저장된 확정 낙찰가.
        long currentPrice,
        long startPrice,
        long bidCount,
        long deadline,
        long listedAt,
        AuctionStatus status,
        long revision,
        // 상향 경매는 null. 하향 경매는 클라이언트가 시간에 따른 가격을 계산한다.
        AuctionDownPricingResponse downPricing
) {
}
