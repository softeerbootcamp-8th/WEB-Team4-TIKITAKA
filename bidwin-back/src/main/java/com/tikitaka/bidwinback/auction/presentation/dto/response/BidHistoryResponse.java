package com.tikitaka.bidwinback.auction.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record BidHistoryResponse(
        @Schema(description = "전체 입찰 횟수", example = "12")
        long bidCount,

        @Schema(description = "최근 입찰 내역. 최대 10건")
        List<BidHistoryItemResponse> bidLog
) {
}
