package com.tikitaka.bidwinback.member.presentation.dto.request;

import jakarta.validation.constraints.NotNull;

public record PointChargeRequest(
        // 1,000원 단위 및 1억원 상한 검증은 서비스에서 수행한다.
        @NotNull(message = "충전 금액은 필수입니다.")
        Long amount
) {
}
