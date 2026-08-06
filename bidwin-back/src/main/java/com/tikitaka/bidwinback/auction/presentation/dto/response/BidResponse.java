package com.tikitaka.bidwinback.auction.presentation.dto.response;

import com.tikitaka.bidwinback.auction.application.BidResult;
import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;

import java.time.LocalDateTime;

public sealed interface BidResponse {

    public static BidResponse from(BidResult result) {
        if (result.status() == BidStatus.SEALED) {
            return new Sealed(
                    result.bidId(),
                    result.auctionId(),
                    result.bidderId(),
                    result.status(),
                    result.bidAt()
            );
        }

        return new Open(
                result.bidId(),
                result.auctionId(),
                result.bidderId(),
                result.price(),
                result.status(),
                result.bidAt()
        );
    }

    record Open(
            Long bidId,
            Long auctionId,
            Long bidderId,
            long price,
            BidStatus status,
            LocalDateTime bidAt
    ) implements BidResponse {
    }

    record Sealed(
            Long bidId,
            Long auctionId,
            Long bidderId,
            BidStatus status,
            LocalDateTime bidAt
    ) implements BidResponse {
    }
}
