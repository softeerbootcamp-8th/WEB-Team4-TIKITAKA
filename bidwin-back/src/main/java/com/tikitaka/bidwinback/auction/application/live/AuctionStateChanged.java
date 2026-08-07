package com.tikitaka.bidwinback.auction.application.live;

/**
 * 트랜잭션 안에서는 식별자만 알리고, 커밋 뒤 DB에서 최신 절대 상태를 다시 읽는다.
 */
public record AuctionStateChanged(long auctionId) {
}
