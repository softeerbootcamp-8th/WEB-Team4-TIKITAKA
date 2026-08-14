package com.tikitaka.bidwinback.auction.infrastructure;

import com.querydsl.core.annotations.QueryProjection;

public record AuctionThumbnailDetails(
        long auctionId,
        String objectKey
) {

    @QueryProjection
    public AuctionThumbnailDetails(
            long auctionId,
            String objectKey
    ) {
        if (objectKey == null) {
            throw new IllegalStateException("경매 썸네일 키가 존재하지 않습니다. auctionId=" + auctionId);
        }
        this.auctionId = auctionId;
        this.objectKey = objectKey;
    }
}
