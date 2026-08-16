package com.tikitaka.bidwinback.auction.presentation.dto.response;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.enums.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record DownAuctionDetailResponse(
        Long auctionId,
        AuctionType auctionType,
        String title,
        String description,
        AuctionCategory category,
        AuctionStatus status,
        @Schema(description = "실시간 상태 버전", example = "3")
        long revision,
        List<String> images,
        long startPrice,
        @Schema(description = "가격 인하 계산 시작 시각(epoch milliseconds)", example = "1786860000000")
        long startedAt,
        @Schema(description = "서버 응답 생성 시각(epoch milliseconds)", example = "1786860300000")
        long serverTime,
        @Schema(description = "경매 마감 시각(epoch milliseconds)", example = "1786863600000")
        long deadline,
        TradeType tradeType,
        AuctionSellerResponse seller,
        @Schema(description = "구매 확정 전에는 null", example = "45000")
        Long finalPrice,
        long minimumPrice,
        long dropPrice,
        @Schema(description = "가격 인하 주기(milliseconds)", example = "300000")
        long priceDropIntervalMs
) implements AuctionDetailResponse {
}
