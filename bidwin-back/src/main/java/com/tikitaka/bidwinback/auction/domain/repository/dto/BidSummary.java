package com.tikitaka.bidwinback.auction.domain.repository.dto;

public record BidSummary(
        Long highestPrice,
        Long bidCount
) {
}
