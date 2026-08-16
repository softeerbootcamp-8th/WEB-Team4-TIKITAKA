package com.tikitaka.bidwinback.member.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordUpdateRequest(
        @Schema(description = "현재 비밀번호", example = "Password!1", format = "password")
        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        @Size(max = 64, message = "현재 비밀번호는 64자 이하여야 합니다.")
        String currentPassword,

        @Schema(description = "새 비밀번호. 8~64자이며 특수문자 1개 이상 포함", example = "NewPassword!1", format = "password")
        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "새 비밀번호는 8자 이상 64자 이하여야 합니다.")
        @Pattern(
                regexp = "^(?=.*[^\\p{L}\\p{N}\\s])\\S+$",
                message = "새 비밀번호는 공백 없이 특수문자를 1개 이상 포함해야 합니다."
        )
        String newPassword,

        @Schema(description = "새 비밀번호 확인", example = "NewPassword!1", format = "password")
        @NotBlank(message = "새 비밀번호 확인은 필수입니다.")
        @Size(min = 8, max = 64, message = "새 비밀번호 확인은 8자 이상 64자 이하여야 합니다.")
        String newPasswordConfirm
) {
}
