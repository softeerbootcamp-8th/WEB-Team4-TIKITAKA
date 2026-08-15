package com.tikitaka.bidwinback.auction.infrastructure;

import com.querydsl.core.annotations.QueryProjection;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;

public record DownPriceSnapshotDetails(
        long auctionId,
        Long price
) {

    @QueryProjection
    public DownPriceSnapshotDetails(
            long auctionId,
            Long price
    ) {
        if (price == null) {
            throw new IllegalStateException("하향 경매 스냅샷 가격이 존재하지 않습니다. auctionId=" + auctionId);
        }
        this.auctionId = auctionId;
        this.price = price;
    }

    AuctionPriceSnapshot toSnapshot() {
        return new AuctionPriceSnapshot(auctionId, price);
    }
}
