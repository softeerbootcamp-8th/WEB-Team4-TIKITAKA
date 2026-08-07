package com.tikitaka.bidwinback.auction.application.live;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;

public record AuctionLiveState(
        long auctionId,
        long revision,
        AuctionType auctionType,
        AuctionStatus status,
        long currentPrice,
        long bidCount
) {
}
