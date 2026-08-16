package com.tikitaka.bidwinback.auction.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 목록 카드에서 하향 경매 가격을 서버 시각 기준으로 계산하는 데 필요한 값. */
public record AuctionDownPricingResponse(
        @Schema(description = "하락 가능한 최저 가격", example = "10000")
        long minimumPrice,
        @Schema(description = "한 주기마다 인하되는 금액", example = "1000")
        long dropPrice,
        @Schema(description = "가격 인하 주기(milliseconds)", example = "300000")
        long priceDropIntervalMs,
        @Schema(description = "가격 인하 계산 시작 시각(epoch milliseconds)", example = "1786860000000")
        long startedAt
) {
}
