package com.tikitaka.bidwinback.auction.domain.repository.dto;

public record AuctionSealedBidCount(
        Long auctionId,
        Long bidCount
) {
}
