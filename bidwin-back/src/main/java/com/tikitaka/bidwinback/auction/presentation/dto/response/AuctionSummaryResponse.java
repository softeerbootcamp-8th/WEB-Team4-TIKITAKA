package com.tikitaka.bidwinback.auction.presentation.dto.response;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;

public record AuctionSummaryResponse(
        Long auctionId,
        AuctionType auctionType,
        String title,
        String sellerName,
        AuctionCategory category,
        // 업로드된 이미지가 없으면 null. 프론트가 자리표시자를 그린다.
        String thumbnailUrl,
        // 상향: 최고 입찰가(없으면 시작가). 하향: asOf 시점까지 내려간 가격.
        long currentPrice,
        long startPrice,
        long bidCount,
        long deadline,
        long listedAt
) {
}
