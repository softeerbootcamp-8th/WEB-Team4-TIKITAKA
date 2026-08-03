package com.tikitaka.bidwinback.upload.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record AuctionImagePresignBatchRequest(
        @NotNull(message = "임시 경매 식별자는 필수입니다.")
        UUID draftId,

        @NotEmpty(message = "이미지는 한 장 이상이어야 합니다.")
        List<@Valid AuctionImagePresignRequest> images
) {
}
