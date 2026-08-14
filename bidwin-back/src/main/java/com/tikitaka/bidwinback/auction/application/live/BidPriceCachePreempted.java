package com.tikitaka.bidwinback.auction.application.live;

/** OPEN 입찰이 Redis에서 선점에 성공했음을 알린다. 트랜잭션이 롤백되면 이 선점을 되돌려야 한다. */
public record BidPriceCachePreempted(long auctionId, long price) {
}
