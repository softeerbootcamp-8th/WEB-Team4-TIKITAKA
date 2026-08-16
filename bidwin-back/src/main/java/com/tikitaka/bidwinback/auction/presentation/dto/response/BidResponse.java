package com.tikitaka.bidwinback.auction.presentation.dto.response;

import com.tikitaka.bidwinback.auction.application.BidResult;
import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(
        description = "공개·비공개 입찰 결과. 비공개 입찰 응답에는 가격이 포함되지 않습니다.",
        oneOf = {BidResponse.Open.class, BidResponse.Sealed.class},
        discriminatorProperty = "status",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "UP", schema = BidResponse.Open.class),
                @DiscriminatorMapping(value = "SEALED", schema = BidResponse.Sealed.class)
        }
)
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
