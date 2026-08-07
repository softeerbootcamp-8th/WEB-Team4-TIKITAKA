package com.tikitaka.bidwinback.auction.application.live;

/** 커밋 후 공개할 일반 입찰 식별자. 밀봉입찰은 마감 전에는 발행하지 않는다. */
public record AuctionBidCreated(long auctionId, long bidId) {
}
