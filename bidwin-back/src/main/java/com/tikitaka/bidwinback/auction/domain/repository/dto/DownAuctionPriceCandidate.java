package com.tikitaka.bidwinback.auction.domain.repository.dto;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;

import java.time.LocalDateTime;

import static com.tikitaka.bidwinback.auction.domain.DownAuctionCurrentPriceCalculator.calculate;

public record DownAuctionPriceCandidate(
        long auctionId,
        long startPrice,
        long minimumPrice,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        long dropPrice,
        long priceDropInterval,
        AuctionStatus status,
        LocalDateTime completedAt,
        Long storedCurrentPrice
) {

    public long sortPriceAt(LocalDateTime asOf) {
        if (status == AuctionStatus.COMPLETED
                && completedAt != null
                && !completedAt.isAfter(asOf)) {
            return requiredStoredCurrentPrice();
        }
        return calculatedPriceAt(asOf);
    }

    public long displayPriceAt(LocalDateTime asOf) {
        if (status == AuctionStatus.COMPLETED) {
            return requiredStoredCurrentPrice();
        }
        return calculatedPriceAt(asOf);
    }

    private long calculatedPriceAt(LocalDateTime asOf) {
        LocalDateTime priceAt = endedAt.isBefore(asOf) ? endedAt : asOf;
        return calculate(
                startPrice,
                minimumPrice,
                dropPrice,
                priceDropInterval,
                startedAt,
                priceAt
        );
    }

    private long requiredStoredCurrentPrice() {
        if (storedCurrentPrice == null) {
            throw new IllegalStateException(
                    "완료된 하향 경매의 저장 현재가가 없습니다. auctionId=" + auctionId
            );
        }
        return storedCurrentPrice;
    }
}
