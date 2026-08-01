package com.tikitaka.bidwinback.auction.presentation.dto.response;

public record AuctionSellerResponse(
        Long sellerId,
        String name,
        String profileImageUrl,
        boolean verified,
        long dealCount
) {
}
