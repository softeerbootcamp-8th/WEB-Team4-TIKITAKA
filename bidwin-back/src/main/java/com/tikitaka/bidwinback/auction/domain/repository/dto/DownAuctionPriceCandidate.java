package com.tikitaka.bidwinback.auction.domain.repository.dto;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;

import java.time.LocalDateTime;

import static com.tikitaka.bidwinback.auction.domain.DownAuctionCurrentPriceCalculator.calculate;

public record DownAuctionPriceCandidate(
        long auctionId,
        long startPrice,
        long minimumPrice,
        LocalDateTime startedAt,
        long dropPrice,
        long priceDropInterval,
        AuctionStatus status,
        Long storedCurrentPrice
) {

    public long currentPriceAt(LocalDateTime asOf) {
        if (status == AuctionStatus.COMPLETED) {
            if (storedCurrentPrice == null) {
                throw new IllegalStateException(
                        "완료된 하향 경매의 저장 현재가가 없습니다. auctionId=" + auctionId
                );
            }
            return storedCurrentPrice;
        }
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
