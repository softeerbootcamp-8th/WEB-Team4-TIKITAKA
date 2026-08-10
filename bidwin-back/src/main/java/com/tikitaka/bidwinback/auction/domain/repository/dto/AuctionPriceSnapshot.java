package com.tikitaka.bidwinback.auction.domain.repository.dto;

public record AuctionPriceSnapshot(
        long auctionId,
        long currentPrice
) {
}
