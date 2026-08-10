package com.tikitaka.bidwinback.auction.infrastructure;

import com.querydsl.core.annotations.QueryProjection;

public record AuctionListMetrics(
        long auctionId,
        long currentPrice,
        long bidCount
) {

    @QueryProjection
    public AuctionListMetrics(
            long auctionId,
            long currentPrice,
            long bidCount
    ) {
        this.auctionId = auctionId;
        this.currentPrice = currentPrice;
        this.bidCount = bidCount;
    }
}
