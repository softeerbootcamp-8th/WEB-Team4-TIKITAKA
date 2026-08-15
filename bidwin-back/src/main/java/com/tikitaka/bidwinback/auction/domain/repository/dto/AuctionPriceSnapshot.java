package com.tikitaka.bidwinback.auction.domain.repository.dto;

/** 가격순 위치를 고정하는 가격과 목록 응답에 전달할 가격을 함께 보관한다. */
public record AuctionPriceSnapshot(
        long auctionId,
        long sortPrice,
        long displayPrice
) {
}
