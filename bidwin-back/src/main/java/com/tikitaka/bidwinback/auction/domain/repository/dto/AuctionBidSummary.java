package com.tikitaka.bidwinback.auction.domain.repository.dto;

public record AuctionBidSummary(
        Long auctionId,
        Long highestPrice,
        Long bidCount
) {
}
