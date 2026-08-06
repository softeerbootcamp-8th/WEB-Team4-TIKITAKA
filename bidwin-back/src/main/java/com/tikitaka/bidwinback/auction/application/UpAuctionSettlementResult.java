package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;

import java.time.LocalDateTime;

public record UpAuctionSettlementResult(
        Long auctionId,
        AuctionStatus status,
        Long winnerId,
        Long finalPrice,
        LocalDateTime settledAt
) {

    public static UpAuctionSettlementResult completed(AuctionTrade trade) {
        return new UpAuctionSettlementResult(
                trade.getAuction().getId(),
                AuctionStatus.COMPLETED,
                trade.getBuyer().getId(),
                trade.getFinalPrice(),
                trade.getPurchasedAt()
        );
    }

    public static UpAuctionSettlementResult unsold(
            Long auctionId,
            LocalDateTime settledAt
    ) {
        return new UpAuctionSettlementResult(
                auctionId,
                AuctionStatus.UNSOLD,
                null,
                null,
                settledAt
        );
    }
}
