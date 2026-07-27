package com.tikitaka.bidwinback.auction.presentation.dto.response;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;

public record AuctionCategoryResponse(String code, String label) {
    public static AuctionCategoryResponse from(AuctionCategory category){
        return new AuctionCategoryResponse(category.name(), category.getLabel());
    }
}
