package com.tikitaka.bidwinback.auction.application.live;

/** 마감으로 밀봉입찰까지 공개된 경매의 최근 입찰 snapshot 발행 요청. */
public record AuctionBidHistoryRevealed(long auctionId, long revision) {
}
