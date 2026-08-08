package com.tikitaka.bidwinback.upload.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record AuctionImagePresignBatchRequest(
        @NotNull(message = "임시 경매 식별자는 필수입니다.")
        UUID draftId,

        @NotEmpty(message = "이미지는 한 장 이상이어야 합니다.")
        @Size(max = 10, message = "이미지는 최대 10장까지 업로드할 수 있습니다.")
        List<@Valid AuctionImagePresignRequest> images
) {
}
