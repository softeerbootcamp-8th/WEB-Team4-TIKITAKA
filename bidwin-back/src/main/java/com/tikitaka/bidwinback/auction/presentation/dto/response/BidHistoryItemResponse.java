package com.tikitaka.bidwinback.auction.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record BidHistoryItemResponse(
        @Schema(description = "공개·비공개 입찰을 구분하는 항목 ID", example = "BID:12")
        String entryId,
        @Schema(description = "마스킹된 입찰자 닉네임", example = "경*왕")
        String bidder,
        @Schema(description = "입찰 금액", example = "25000")
        long amount,
        @Schema(description = "입찰 시각(epoch milliseconds)", example = "1786860000000")
        long biddedAt
) {
}
