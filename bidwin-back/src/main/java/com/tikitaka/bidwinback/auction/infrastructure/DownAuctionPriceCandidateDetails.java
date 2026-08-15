package com.tikitaka.bidwinback.auction.infrastructure;

import com.querydsl.core.annotations.QueryProjection;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.repository.dto.DownAuctionPriceCandidate;

import java.time.LocalDateTime;

public record DownAuctionPriceCandidateDetails(
        long auctionId,
        long startPrice,
        long minimumPrice,
        LocalDateTime startedAt,
        long dropPrice,
        long priceDropInterval,
        AuctionStatus status,
        LocalDateTime completedAt,
        Long currentPrice
) {

    @QueryProjection
    public DownAuctionPriceCandidateDetails(
            long auctionId,
            long startPrice,
            long minimumPrice,
            LocalDateTime startedAt,
            long dropPrice,
            long priceDropInterval,
            AuctionStatus status,
            LocalDateTime completedAt,
            Long currentPrice
    ) {
        this.auctionId = auctionId;
        this.startPrice = startPrice;
        this.minimumPrice = minimumPrice;
        this.startedAt = startedAt;
        this.dropPrice = dropPrice;
        this.priceDropInterval = priceDropInterval;
        this.status = status;
        this.completedAt = completedAt;
        this.currentPrice = currentPrice;
    }

    DownAuctionPriceCandidate toCandidate() {
        return new DownAuctionPriceCandidate(
                auctionId,
                startPrice,
                minimumPrice,
                startedAt,
                dropPrice,
                priceDropInterval,
                status,
                completedAt,
                currentPrice
        );
    }
}
