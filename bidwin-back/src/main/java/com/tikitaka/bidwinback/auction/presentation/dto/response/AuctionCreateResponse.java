package com.tikitaka.bidwinback.auction.presentation.dto.response;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;

public record AuctionCreateResponse(
        Long auctionId
) {

    public static AuctionCreateResponse from(Auction auction) {
        return new AuctionCreateResponse(auction.getId());
    }
}
