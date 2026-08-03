package com.tikitaka.bidwinback.auction.presentation.dto.response;

import java.util.List;

public record BidHistoryResponse(
        long bidCount,
        List<BidHistoryItemResponse> bidLog
) {
}
