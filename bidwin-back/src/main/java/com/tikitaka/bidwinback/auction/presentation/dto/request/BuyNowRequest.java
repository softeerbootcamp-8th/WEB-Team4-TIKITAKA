package com.tikitaka.bidwinback.auction.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BuyNowRequest(
        @NotBlank(message = "멱등 키는 필수입니다.")
        @Size(max = 100, message = "멱등 키는 100자 이하여야 합니다.")
        @Pattern(
                regexp = "[A-Za-z0-9][A-Za-z0-9._:-]*",
                message = "멱등 키 형식이 올바르지 않습니다."
        )
        String idempotencyKey
) {
}
