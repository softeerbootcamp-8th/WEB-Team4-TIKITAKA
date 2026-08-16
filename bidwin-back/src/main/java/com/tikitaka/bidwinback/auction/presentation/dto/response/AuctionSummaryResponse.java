package com.tikitaka.bidwinback.auction.presentation.dto.response;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import io.swagger.v3.oas.annotations.media.Schema;

public record AuctionSummaryResponse(
        Long auctionId,
        AuctionType auctionType,
        String title,
        String sellerName,
        AuctionCategory category,
        // 업로드된 이미지가 없으면 null. 프론트가 자리표시자를 그린다.
        @Schema(description = "대표 이미지 URL. 이미지가 없으면 null")
        String thumbnailUrl,
        // 상향: 최고 입찰가(없으면 시작가). 하향: asOf 시점까지 내려간 가격.
        @Schema(description = "목록 기준 시각의 현재가", example = "25000")
        long currentPrice,
        long startPrice,
        long bidCount,
        @Schema(description = "경매 마감 시각(epoch milliseconds)", example = "1786863600000")
        long deadline,
        @Schema(description = "경매 등록 시각(epoch milliseconds)", example = "1786860000000")
        long listedAt,
        AuctionStatus status,
        @Schema(description = "실시간 상태 버전", example = "3")
        long revision,
        // 상향 경매는 null. 하향 경매는 클라이언트가 시간에 따른 가격을 계산한다.
        @Schema(description = "하향 경매 가격 계산 정보. 상향 경매는 null")
        AuctionDownPricingResponse downPricing
) {
}
