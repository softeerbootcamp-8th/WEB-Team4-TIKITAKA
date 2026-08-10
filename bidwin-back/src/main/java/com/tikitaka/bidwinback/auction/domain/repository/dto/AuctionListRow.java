package com.tikitaka.bidwinback.auction.domain.repository.dto;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;

import java.time.LocalDateTime;

public record AuctionListRow(
        long auctionId,
        AuctionType auctionType,
        String title,
        String sellerName,
        AuctionCategory category,
        String thumbnailObjectKey,
        long currentPrice,
        long startPrice,
        long bidCount,
        LocalDateTime deadline,
        LocalDateTime listedAt,
        AuctionStatus status,
        long revision,
        Long minimumPrice,
        Long dropPrice,
        Long priceDropInterval,
        LocalDateTime startedAt
) {
}
