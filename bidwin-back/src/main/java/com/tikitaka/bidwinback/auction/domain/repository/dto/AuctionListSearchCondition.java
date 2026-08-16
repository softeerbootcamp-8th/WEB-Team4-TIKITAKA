package com.tikitaka.bidwinback.auction.domain.repository.dto;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionListStatusFilter;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;

import java.time.LocalDateTime;

public record AuctionListSearchCondition(
        AuctionType auctionType,
        AuctionSort sort,
        String keyword,
        AuctionListStatusFilter status,
        AuctionCategory category,
        LocalDateTime asOf
) {
    public AuctionListSearchCondition(
            AuctionType auctionType,
            AuctionSort sort,
            String keyword,
            LocalDateTime asOf
    ) {
        this(
                auctionType,
                sort,
                keyword,
                AuctionListStatusFilter.ACTIVE,
                null,
                asOf
        );
    }
}
