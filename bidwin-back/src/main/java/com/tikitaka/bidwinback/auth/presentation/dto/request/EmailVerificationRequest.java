package com.tikitaka.bidwinback.auth.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailVerificationRequest(
        @NotBlank(message = "이메일 인증 토큰은 필수입니다.")
        @Size(max = 255, message = "이메일 인증 토큰은 255자 이하여야 합니다.")
        String token
) {
}
