package com.tikitaka.bidwinback.upload.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProfileImagePresignRequest(
        @Schema(description = "확장자를 포함한 원본 파일명", example = "profile.png")
        @NotBlank(message = "파일명은 필수입니다.")
        @Size(max = 255, message = "파일명은 255자 이하여야 합니다.")
        String fileName,

        @Schema(
                description = "파일 MIME 타입",
                example = "image/png",
                allowableValues = {"image/jpeg", "image/png", "image/webp"}
        )
        @NotBlank(message = "파일 형식은 필수입니다.")
        @Size(max = 100, message = "파일 형식은 100자 이하여야 합니다.")
        String contentType,

        @Schema(description = "파일 크기(byte). 최대 5MB", example = "524288")
        @NotNull(message = "파일 크기는 필수입니다.")
        @Positive(message = "파일 크기는 0보다 커야 합니다.")
        @Max(value = 5_242_880, message = "파일 크기는 5MB 이하여야 합니다.")
        Long size
) {
}
