package com.tikitaka.bidwinback.auction.presentation.dto.response;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.enums.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record UpAuctionDetailResponse(
        Long auctionId,
        AuctionType auctionType,
        String title,
        String description,
        AuctionCategory category,
        AuctionStatus status,
        @Schema(description = "실시간 상태 버전", example = "3")
        long revision,
        List<String> images,
        long startPrice,
        @Schema(description = "경매 마감 시각(epoch milliseconds)", example = "1786863600000")
        long deadline,
        @Schema(description = "서버 응답 생성 시각(epoch milliseconds)", example = "1786860000000")
        long serverTime,
        @Schema(description = "비공개 입찰 시작 시각(epoch milliseconds)", example = "1786863300000")
        long sealedBidStartsAt,
        TradeType tradeType,
        AuctionSellerResponse seller,
        @Schema(description = "즉시구매 미지원 시 null", example = "50000")
        Long buyNowPrice,
        long currentPrice,
        long bidCount
) implements AuctionDetailResponse {
}
