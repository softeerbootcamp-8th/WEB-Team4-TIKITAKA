package com.tikitaka.bidwinback.upload.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record AuctionImagePresignBatchRequest(
        @Schema(description = "경매 이미지 업로드 draft ID", example = "550e8400-e29b-41d4-a716-446655440000")
        @NotNull(message = "임시 경매 식별자는 필수입니다.")
        UUID draftId,

        @Schema(description = "업로드할 이미지 정보. 1~10개")
        @NotEmpty(message = "이미지는 한 장 이상이어야 합니다.")
        @Size(max = 10, message = "이미지는 최대 10장까지 업로드할 수 있습니다.")
        List<@Valid AuctionImagePresignRequest> images
) {
}
