package com.tikitaka.bidwinback.mypage.presentation.dto.response;

public record DownPricingResponse(
        long startPrice,
        long minimumPrice,
        long dropPrice,
        long priceDropIntervalMs,
        long startedAt,
        // 클라이언트 시계가 서버와 어긋나도 현재가를 같게 계산하도록 DB 기준 현재 시각을 함께 내려준다.
        long serverTime
) {
}
