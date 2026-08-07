package com.tikitaka.bidwinback.mypage.presentation.dto.response;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;

public record MySaleRecordResponse(
        long auctionId,
        String title,
        String thumbnailUrl,
        AuctionType auctionType,
        long startPrice,
        long price,
        AuctionStatus status,
        long listedAt
) {
}
