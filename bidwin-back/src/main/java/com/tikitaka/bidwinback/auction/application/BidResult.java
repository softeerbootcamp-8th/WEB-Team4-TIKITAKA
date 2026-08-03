package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Bid;
import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;

import java.time.LocalDateTime;

public record BidResult(
        Long bidId,
        Long auctionId,
        Long bidderId,
        long price,
        BidStatus status,
        LocalDateTime bidAt
) {

    public static BidResult from(Bid bid) {
        return new BidResult(
                bid.getId(),
                bid.getAuction().getId(),
                bid.getBidder().getId(),
                bid.getPrice(),
                bid.getStatus(),
                bid.getCreatedAt()
        );
    }
}
