package com.tikitaka.bidwinback.upload.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AuctionImagePresignResponse(
        @Schema(description = "경매 등록 요청에 사용할 업로드 ID", example = "c56a4180-65aa-42ec-a945-5fd21dec0538")
        UUID uploadId,

        @Schema(description = "S3 PUT 업로드 URL")
        String presignedUrl,

        @Schema(description = "S3 업로드 요청에 그대로 포함해야 하는 서명 헤더")
        Map<String, List<String>> signedHeaders,

        @Schema(description = "Presigned URL 만료 시각")
        Instant expiresAt
) {
}
