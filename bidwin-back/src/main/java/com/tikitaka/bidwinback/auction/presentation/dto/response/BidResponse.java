package com.tikitaka.bidwinback.auction.presentation.dto.response;

import com.tikitaka.bidwinback.auction.application.BidResult;
import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;

import java.time.LocalDateTime;

public record BidResponse(
        Long bidId,
        Long auctionId,
        Long bidderId,
        long price,
        BidStatus status,
        LocalDateTime bidAt
) {

    public static BidResponse from(BidResult result) {
        return new BidResponse(
                result.bidId(),
                result.auctionId(),
                result.bidderId(),
                result.price(),
                result.status(),
                result.bidAt()
        );
    }
}
