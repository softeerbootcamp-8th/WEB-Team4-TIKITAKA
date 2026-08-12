package com.tikitaka.bidwinback.auction.application.live;

import java.time.LocalDateTime;

/** 트랜잭션 커밋 후 입찰가 캐시에 반영할 공개입찰 가격. */
public record OpenBidAccepted(long auctionId, long price, LocalDateTime endedAt) {
}
