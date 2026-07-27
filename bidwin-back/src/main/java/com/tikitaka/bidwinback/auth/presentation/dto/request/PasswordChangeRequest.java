package com.tikitaka.bidwinback.auth.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank(message = "비밀번호 재설정 토큰은 필수입니다.")
        @Size(max = 255, message = "비밀번호 재설정 토큰은 255자 이하여야 합니다.")
        String token,

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "새 비밀번호는 8자 이상 64자 이하여야 합니다.")
        @Pattern(
                regexp = "^(?=.*[^\\p{L}\\p{N}\\s])\\S+$",
                message = "새 비밀번호는 공백 없이 특수문자를 1개 이상 포함해야 합니다."
        )
        String newPassword,

        @NotBlank(message = "새 비밀번호 확인은 필수입니다.")
        @Size(min = 8, max = 64, message = "새 비밀번호 확인은 8자 이상 64자 이하여야 합니다.")
        String newPasswordConfirm
) {
}
