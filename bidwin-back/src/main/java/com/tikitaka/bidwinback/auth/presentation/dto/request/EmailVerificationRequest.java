package com.tikitaka.bidwinback.auth.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailVerificationRequest(
        @Schema(description = "인증 메일 링크에 포함된 토큰", example = "email-verification-token")
        @NotBlank(message = "이메일 인증 토큰은 필수입니다.")
        @Size(max = 255, message = "이메일 인증 토큰은 255자 이하여야 합니다.")
        String token
) {
}
