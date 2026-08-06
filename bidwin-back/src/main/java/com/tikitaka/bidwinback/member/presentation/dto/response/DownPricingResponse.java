package com.tikitaka.bidwinback.member.presentation.dto.response;

public record DownPricingResponse(
        long minimumPrice,
        long dropPrice,
        long priceDropIntervalMs,
        long startedAt
) {
}
