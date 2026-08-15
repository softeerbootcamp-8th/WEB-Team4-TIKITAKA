package com.tikitaka.bidwinback.auction.application;

public record AuctionListCounts(
        long all,
        long up,
        long down
) {
    public AuctionListCounts {
        if (all < 0 || up < 0 || down < 0) {
            throw new IllegalArgumentException("경매 목록 count는 음수일 수 없습니다.");
        }
    }
}
