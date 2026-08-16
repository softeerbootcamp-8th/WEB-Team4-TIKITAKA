package com.tikitaka.bidwinback.auction.infrastructure;

import com.querydsl.core.annotations.QueryProjection;

/** 추천순 타입별 후보를 병합하기 위한 최소 조회 결과다. */
public record AuctionRecommendedCandidate(
        long auctionId,
        long bidCount
) {

    @QueryProjection
    public AuctionRecommendedCandidate(long auctionId, long bidCount) {
        this.auctionId = auctionId;
        this.bidCount = bidCount;
    }
}
