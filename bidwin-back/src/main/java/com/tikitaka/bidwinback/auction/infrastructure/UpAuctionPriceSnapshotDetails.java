package com.tikitaka.bidwinback.auction.infrastructure;

import com.querydsl.core.annotations.QueryProjection;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;

public record UpAuctionPriceSnapshotDetails(
        long auctionId,
        Long currentPrice
) {

    @QueryProjection
    public UpAuctionPriceSnapshotDetails(
            long auctionId,
            Long currentPrice
    ) {
        if (currentPrice == null) {
            throw new IllegalStateException("상향 경매 현재 가격이 존재하지 않습니다. auctionId=" + auctionId);
        }
        this.auctionId = auctionId;
        this.currentPrice = currentPrice;
    }

    AuctionPriceSnapshot toSnapshot() {
        return new AuctionPriceSnapshot(auctionId, currentPrice, currentPrice);
    }
}
