package com.tikitaka.bidwinback.upload.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AuctionImagePresignRequest(
        @NotBlank(message = "파일명은 필수입니다.")
        @Size(max = 255, message = "파일명은 255자 이하여야 합니다.")
        String fileName,

        @NotBlank(message = "파일 형식은 필수입니다.")
        @Size(max = 100, message = "파일 형식은 100자 이하여야 합니다.")
        String contentType,

        @NotNull(message = "파일 크기는 필수입니다.")
        @Positive(message = "파일 크기는 0보다 커야 합니다.")
        @Max(value = 10_485_760, message = "파일 크기는 10MB 이하여야 합니다.")
        Long size
) {
}
