package com.tikitaka.bidwinback.auction.presentation.dto.request;

import jakarta.validation.constraints.Positive;

public record BidRequest(
        // 호가 단위·현재가 비교 같은 정합성 검증은 후속 작업에서 붙인다.
        @Positive(message = "입찰가는 0보다 커야 합니다.")
        long price
) {
}
