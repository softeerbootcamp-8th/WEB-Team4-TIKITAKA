package com.tikitaka.bidwinback.mypage.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileImageUpdateRequest(
        @Schema(description = "프로필 이미지 Presigned URL 응답의 objectKey", example = "profile-images/1/550e8400.webp")
        @NotBlank(message = "프로필 이미지 키는 필수입니다.")
        @Size(max = 100, message = "프로필 이미지 키는 100자 이하여야 합니다.")
        String objectKey
) {
}
