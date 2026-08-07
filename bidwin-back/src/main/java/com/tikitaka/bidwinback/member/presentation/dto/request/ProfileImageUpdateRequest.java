package com.tikitaka.bidwinback.member.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileImageUpdateRequest(
        @NotBlank(message = "프로필 이미지 키는 필수입니다.")
        @Size(max = 100, message = "프로필 이미지 키는 100자 이하여야 합니다.")
        String objectKey
) {
}
