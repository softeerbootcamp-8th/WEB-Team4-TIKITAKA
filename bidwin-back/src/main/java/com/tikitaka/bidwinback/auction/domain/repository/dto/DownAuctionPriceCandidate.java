package com.tikitaka.bidwinback.auction.domain.repository.dto;

import java.time.LocalDateTime;

import static com.tikitaka.bidwinback.auction.domain.DownAuctionCurrentPriceCalculator.calculate;

public record DownAuctionPriceCandidate(
        long auctionId,
        long startPrice,
        long minimumPrice,
        LocalDateTime startedAt,
        long dropPrice,
        long priceDropInterval
) {

    public long currentPriceAt(LocalDateTime asOf) {
        return calculate(
                startPrice,
                minimumPrice,
                dropPrice,
                priceDropInterval,
                startedAt,
                asOf
        );
    }
}
