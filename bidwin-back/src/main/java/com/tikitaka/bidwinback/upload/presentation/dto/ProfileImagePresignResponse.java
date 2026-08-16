package com.tikitaka.bidwinback.upload.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ProfileImagePresignResponse(
        @Schema(description = "S3 PUT 업로드 URL")
        String presignedUrl,

        @Schema(description = "프로필 이미지 변경 요청에 사용할 object key", example = "profile-images/1/550e8400.webp")
        String objectKey,

        @Schema(description = "S3 업로드 요청에 그대로 포함해야 하는 서명 헤더")
        Map<String, List<String>> signedHeaders,

        @Schema(description = "Presigned URL 만료 시각")
        Instant expiresAt
) {
}
