package com.tikitaka.bidwinback.auction.presentation.dto.response;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.enums.TradeType;

import java.util.List;

public record DownAuctionDetailResponse(
        Long auctionId,
        AuctionType auctionType,
        String title,
        String description,
        AuctionCategory category,
        AuctionStatus status,
        List<String> images,
        long startPrice,
        long startedAt,
        long serverTime,
        long deadline,
        TradeType tradeType,
        String contact,
        AuctionSellerResponse seller,
        long minimumPrice,
        long dropPrice,
        long priceDropIntervalMs
) implements AuctionDetailResponse {
}
