package com.tikitaka.bidwinback.auction.domain;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class DownAuctionCurrentPriceCalculator {

    private DownAuctionCurrentPriceCalculator() {
    }

    public static long calculate(
            long startPrice,
            long minimumPrice,
            long dropPrice,
            long priceDropInterval,
            LocalDateTime startedAt,
            LocalDateTime asOf
    ) {
        validate(
                startPrice,
                minimumPrice,
                dropPrice,
                priceDropInterval,
                startedAt,
                asOf
        );

        long elapsedMinutes = Math.max(0, ChronoUnit.MINUTES.between(startedAt, asOf));
        long elapsedDrops = elapsedMinutes / priceDropInterval;
        long priceRange = startPrice - minimumPrice;
        long dropsBeforeFloor = priceRange / dropPrice;

        if (elapsedDrops > dropsBeforeFloor) {
            return minimumPrice;
        }
        return startPrice - elapsedDrops * dropPrice;
    }

    private static void validate(
            long startPrice,
            long minimumPrice,
            long dropPrice,
            long priceDropInterval,
            LocalDateTime startedAt,
            LocalDateTime asOf
    ) {
        if (startedAt == null
                || asOf == null
                || priceDropInterval <= 0
                || dropPrice <= 0
                || minimumPrice < 0
                || minimumPrice > startPrice) {
            throw new IllegalStateException("하향 경매 가격 설정이 올바르지 않습니다.");
        }
    }
}
