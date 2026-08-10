package com.tikitaka.bidwinback.auction.domain.repository.dto;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;

import java.time.LocalDateTime;

public record AuctionListSearchCondition(
        AuctionType auctionType,
        AuctionSort sort,
        String keyword,
        LocalDateTime asOf
) {
}
