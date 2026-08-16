package com.tikitaka.bidwinback.auction.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "경매 방식별 상세 응답",
        oneOf = {UpAuctionDetailResponse.class, DownAuctionDetailResponse.class},
        discriminatorProperty = "auctionType"
)
public sealed interface AuctionDetailResponse
        permits UpAuctionDetailResponse, DownAuctionDetailResponse {
}
