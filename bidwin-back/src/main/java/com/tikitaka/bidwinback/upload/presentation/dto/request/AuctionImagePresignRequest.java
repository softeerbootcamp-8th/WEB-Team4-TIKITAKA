package com.tikitaka.bidwinback.upload.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AuctionImagePresignRequest(
        @Schema(description = "확장자를 포함한 원본 파일명", example = "chair.webp")
        @NotBlank(message = "파일명은 필수입니다.")
        @Size(max = 255, message = "파일명은 255자 이하여야 합니다.")
        String fileName,

        @Schema(
                description = "파일 MIME 타입",
                example = "image/webp",
                allowableValues = {"image/jpeg", "image/png", "image/webp"}
        )
        @NotBlank(message = "파일 형식은 필수입니다.")
        @Size(max = 100, message = "파일 형식은 100자 이하여야 합니다.")
        String contentType,

        @Schema(description = "파일 크기(byte). 최대 10MB", example = "1048576")
        @NotNull(message = "파일 크기는 필수입니다.")
        @Positive(message = "파일 크기는 0보다 커야 합니다.")
        @Max(value = 10_485_760, message = "파일 크기는 10MB 이하여야 합니다.")
        Long size,

        @Schema(description = "파일 내용의 SHA-256 해시를 Base64로 인코딩한 값", example = "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=")
        @NotBlank(message = "파일 체크섬은 필수입니다.")
        @Pattern(
                regexp = "^[A-Za-z0-9+/]{43}=$",
                message = "파일 체크섬 형식이 올바르지 않습니다."
        )
        String checksumSha256
) {
}
