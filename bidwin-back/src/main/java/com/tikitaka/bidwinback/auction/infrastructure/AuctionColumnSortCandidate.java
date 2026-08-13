package com.tikitaka.bidwinback.auction.infrastructure;

import com.querydsl.core.annotations.QueryProjection;

import java.time.LocalDateTime;

/**
 * 최신순과 마감임박순의 completed_at 분기를 병합하기 위한 최소 조회 결과다.
 */
public record AuctionColumnSortCandidate(
        long auctionId,
        LocalDateTime sortAt
) {

    @QueryProjection
    public AuctionColumnSortCandidate(
            long auctionId,
            LocalDateTime sortAt
    ) {
        this.auctionId = auctionId;
        this.sortAt = sortAt;
    }
}
