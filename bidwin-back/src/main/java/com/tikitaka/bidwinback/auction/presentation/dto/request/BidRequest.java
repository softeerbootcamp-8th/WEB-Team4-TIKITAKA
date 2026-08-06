package com.tikitaka.bidwinback.auction.presentation.dto.request;

import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BidRequest(
        @NotNull(message = "입찰 유형은 필수입니다.")
        BidStatus status,

        @Positive(message = "입찰가는 0보다 커야 합니다.")
        long price
) {
}
