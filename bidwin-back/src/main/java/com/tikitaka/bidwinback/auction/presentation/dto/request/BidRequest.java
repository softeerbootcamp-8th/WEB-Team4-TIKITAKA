package com.tikitaka.bidwinback.auction.presentation.dto.request;

import com.tikitaka.bidwinback.auction.domain.enums.BidType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BidRequest(
        @Schema(description = "입찰가. 현재가보다 호가 단위 이상 높아야 함", example = "25000")
        @Positive(message = "입찰가는 0보다 커야 합니다.")
        long price,

        @Schema(description = "입찰 유형. SEALED 입찰은 경매 종료 전 가격 비공개", example = "OPEN")
        @NotNull(message = "입찰 유형을 입력해주세요.")
        BidType bidType
) {
}
