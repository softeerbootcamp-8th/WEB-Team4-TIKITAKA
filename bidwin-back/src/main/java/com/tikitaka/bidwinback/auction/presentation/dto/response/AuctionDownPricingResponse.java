package com.tikitaka.bidwinback.auction.presentation.dto.response;

/** 목록 카드에서 하향 경매 가격을 서버 시각 기준으로 계산하는 데 필요한 값. */
public record AuctionDownPricingResponse(
        long minimumPrice,
        long dropPrice,
        long priceDropIntervalMs,
        long startedAt
) {
}
