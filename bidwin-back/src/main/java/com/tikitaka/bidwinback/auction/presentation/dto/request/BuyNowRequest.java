package com.tikitaka.bidwinback.auction.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BuyNowRequest(
        // 요구사항: 재시도와 중복 요청 식별을 위한 유효한 멱등 키를 필수로 받는다.
        @Schema(description = "구매 시도별 고유 멱등 키. 같은 요청 재시도에는 같은 값을 사용", example = "buy-1-550e8400")
        @NotBlank(message = "멱등 키는 필수입니다.")
        @Size(max = 100, message = "멱등 키는 100자 이하여야 합니다.")
        @Pattern(
                regexp = "[A-Za-z0-9][A-Za-z0-9._:-]*",
                message = "멱등 키 형식이 올바르지 않습니다."
        )
        String idempotencyKey
) {
}
