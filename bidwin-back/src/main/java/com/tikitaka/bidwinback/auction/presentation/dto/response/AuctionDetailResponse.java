package com.tikitaka.bidwinback.auction.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "경매 방식별 상세 응답",
        oneOf = {UpAuctionDetailResponse.class, DownAuctionDetailResponse.class},
        discriminatorProperty = "auctionType",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "UP", schema = UpAuctionDetailResponse.class),
                @DiscriminatorMapping(value = "DOWN", schema = DownAuctionDetailResponse.class)
        }
)
public sealed interface AuctionDetailResponse
        permits UpAuctionDetailResponse, DownAuctionDetailResponse {
}
