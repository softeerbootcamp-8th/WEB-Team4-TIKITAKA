package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListRow;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionDownPricingResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionListResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionSummaryResponse;
import com.tikitaka.bidwinback.global.storage.ImageUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.OptionalLong;

@Service
@RequiredArgsConstructor
public class AuctionListService {

    private static final int DEFAULT_PAGE_SIZE = 16;
    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final AuctionListCountCache countCache;
    private final AuctionListDbQuery auctionListDbQuery;
    private final ImageUrlResolver imageUrlResolver;

    public AuctionListResponse getList(AuctionListQuery query) {
        int size = normalizedSize(query.size());
        OptionalLong cachedTotalCount = findCachedTotalCount(query);
        AuctionListDbQuery.DbPage dbPage = auctionListDbQuery.findPage(
                query,
                size,
                cachedTotalCount
        );
        List<AuctionSummaryResponse> pageItems = dbPage.items().stream()
                .map(this::toSummary)
                .toList();

        return new AuctionListResponse(
                pageItems,
                toEpochMilli(dbPage.serverTime()),
                toEpochMilli(dbPage.asOf()),
                dbPage.currentPage(),
                dbPage.totalPages(),
                dbPage.totalCount()
        );
    }

    private OptionalLong findCachedTotalCount(AuctionListQuery query) {
        if (!isCountCacheEligible(query)) {
            return OptionalLong.empty();
        }
        try {
            return countCache.find(AuctionListCountScope.from(query.auctionType()));
        } catch (RuntimeException exception) {
            return OptionalLong.empty();
        }
    }

    private boolean isCountCacheEligible(AuctionListQuery query) {
        if (query.keyword() != null && !query.keyword().isBlank()) {
            return false;
        }
        if (query.status() != null) {
            return false;
        }
        if (query.categories() != null && !query.categories().isEmpty()) {
            return false;
        }
        if (query.auctionType() != null) {
            return true;
        }
        return switch (query.sort()) {
            case RECOMMENDED, DEADLINE, LATEST -> true;
            case PRICE_LOW, PRICE_HIGH -> false;
        };
    }

    private AuctionSummaryResponse toSummary(AuctionListRow row) {
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

    private int normalizedSize(int requestedSize) {
        if (requestedSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requestedSize, MAX_PAGE_SIZE);
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(SERVICE_ZONE).toInstant().toEpochMilli();
    }
}
