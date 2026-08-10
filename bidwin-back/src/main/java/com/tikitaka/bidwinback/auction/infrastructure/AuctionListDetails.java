package com.tikitaka.bidwinback.auction.infrastructure;

import com.querydsl.core.annotations.QueryProjection;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListRow;

import java.time.LocalDateTime;

public record AuctionListDetails(
        long auctionId,
        Long downAuctionId,
        String title,
        String sellerName,
        AuctionCategory category,
        long startPrice,
        LocalDateTime deadline,
        LocalDateTime listedAt,
        AuctionStatus status,
        long revision,
        LocalDateTime startedAt,
        Long minimumPrice,
        Long dropPrice,
        Long priceDropInterval
) {

    @QueryProjection
    public AuctionListDetails(
            long auctionId,
            Long downAuctionId,
            String title,
            String sellerName,
            AuctionCategory category,
            long startPrice,
            LocalDateTime deadline,
            LocalDateTime listedAt,
            AuctionStatus status,
            long revision,
            LocalDateTime startedAt,
            Long minimumPrice,
            Long dropPrice,
            Long priceDropInterval
    ) {
        this.auctionId = auctionId;
        this.downAuctionId = downAuctionId;
        this.title = title;
        this.sellerName = sellerName;
        this.category = category;
        this.startPrice = startPrice;
        this.deadline = deadline;
        this.listedAt = listedAt;
        this.status = status;
        this.revision = revision;
        this.startedAt = startedAt;
        this.minimumPrice = minimumPrice;
        this.dropPrice = dropPrice;
        this.priceDropInterval = priceDropInterval;
    }

    AuctionType auctionType() {
        return downAuctionId == null ? AuctionType.UP : AuctionType.DOWN;
    }

    AuctionListRow toRow(
            AuctionListMetrics metrics,
            String thumbnailObjectKey
    ) {
        return new AuctionListRow(
                auctionId,
                auctionType(),
                title,
                sellerName,
                category,
                thumbnailObjectKey,
                metrics.currentPrice(),
                startPrice,
                metrics.bidCount(),
                deadline,
                listedAt,
                status,
                revision,
                minimumPrice,
                dropPrice,
                priceDropInterval,
                startedAt
        );
    }
}
