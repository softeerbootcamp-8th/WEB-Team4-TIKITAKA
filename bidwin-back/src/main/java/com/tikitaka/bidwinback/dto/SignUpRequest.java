package com.tikitaka.bidwinback.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 320, message = "이메일은 320자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
        @Pattern(
                regexp = "^(?=.*[^\\p{L}\\p{N}\\s])\\S+$",
                message = "비밀번호는 공백 없이 특수문자를 1개 이상 포함해야 합니다."
        )
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 17, message = "이름은 17자 이하여야 합니다.")
        String name,

        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(
                regexp = "^01[016789]\\d{7,8}$",
                message = "전화번호는 하이픈 없이 올바른 형식으로 입력해야 합니다."
        )
        String phoneNumber,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하여야 합니다.")
        String nickname
) {
}
