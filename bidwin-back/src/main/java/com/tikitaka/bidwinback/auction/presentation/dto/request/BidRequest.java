package com.tikitaka.bidwinback.auction.presentation.dto.request;

import com.tikitaka.bidwinback.auction.domain.enums.BidType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BidRequest(
        @Positive(message = "입찰가는 0보다 커야 합니다.")
        long price,
        @NotNull(message = "입찰 유형을 입력해주세요.")
        BidType bidType
) {
}
