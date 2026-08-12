package com.tikitaka.bidwinback.auction.application.live;

import java.time.LocalDateTime;

/** 커밋 후 상향 경매의 입찰가 캐시(BidPriceCache)를 시작가로 초기화하기 위한 정보. */
public record AuctionCreated(long auctionId, long startPrice, LocalDateTime endedAt) {
}
