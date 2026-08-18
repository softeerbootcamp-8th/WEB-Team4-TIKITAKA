package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListRow;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionDownPricingResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionSummaryResponse;
import com.tikitaka.bidwinback.global.storage.ImageUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class AuctionSummaryResponseMapper {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final ImageUrlResolver imageUrlResolver;

    public AuctionSummaryResponse toSummary(AuctionListRow row) {
        return new AuctionSummaryResponse(
                row.auctionId(),
                row.auctionType(),
                row.title(),
                row.sellerName(),
                row.category(),
                resolveThumbnail(row.thumbnailObjectKey()),
                row.currentPrice(),
                row.startPrice(),
                row.bidCount(),
                toEpochMilli(row.deadline()),
                toEpochMilli(row.listedAt()),
                row.status(),
                row.revision(),
                downPricing(row)
        );
    }

    private AuctionDownPricingResponse downPricing(AuctionListRow row) {
        if (row.auctionType() != AuctionType.DOWN) {
            return null;
        }
        return new AuctionDownPricingResponse(
                row.minimumPrice(),
                row.dropPrice(),
                Duration.ofMinutes(row.priceDropInterval()).toMillis(),
                toEpochMilli(row.startedAt())
        );
    }

    private String resolveThumbnail(String objectKey) {
        return objectKey != null ? imageUrlResolver.resolve(objectKey) : null;
    }

    long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(SERVICE_ZONE).toInstant().toEpochMilli();
    }
}
