package com.tikitaka.bidwinback.upload.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record AuctionImageDraftResponse(
        @Schema(description = "경매 이미지 업로드와 경매 등록 요청에서 함께 사용할 draft ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID draftId
) {
}
