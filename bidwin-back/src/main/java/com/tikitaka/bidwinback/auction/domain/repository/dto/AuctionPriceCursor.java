package com.tikitaka.bidwinback.auction.domain.repository.dto;

public record AuctionPriceCursor(
        long priceBound,
        long auctionId
) {
}
