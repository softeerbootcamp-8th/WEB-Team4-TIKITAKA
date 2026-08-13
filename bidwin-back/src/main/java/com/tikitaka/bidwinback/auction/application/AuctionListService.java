package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionListQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListRow;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListSearchCondition;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionDownPricingResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionListResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionSummaryResponse;
import com.tikitaka.bidwinback.global.storage.ImageUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuctionListService {

    private static final int FIRST_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 16;
    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final AuctionRepository auctionRepository;
    private final AuctionListQueryRepository auctionListQueryRepository;
    private final AuctionPricePageQuery auctionPricePageQuery;
    private final ImageUrlResolver imageUrlResolver;

    @Transactional(readOnly = true)
    public AuctionListResponse getList(AuctionListQuery query) {
        LocalDateTime serverTime = auctionRepository.currentDatabaseTime();
        LocalDateTime asOf = query.asOf() != null ? query.asOf() : serverTime;
        int size = normalizedSize(query.size());

        // 상태·카테고리는 API 계약만 먼저 열고, 실제 조회 반영은 별도 작업에서 다룬다.
        AuctionListSearchCondition condition = new AuctionListSearchCondition(
                query.auctionType(),
                query.sort(),
                query.keyword(),
                asOf
        );
        long totalCount = auctionListQueryRepository.count(condition);
        int totalPages = totalPages(totalCount, size);
        int currentPage = Math.min(Math.max(FIRST_PAGE, query.page()), totalPages);
        long offset = (long) (currentPage - FIRST_PAGE) * size;

        List<AuctionSummaryResponse> pageItems = totalCount == 0
                ? List.of()
                : findPage(condition, currentPage, size, totalCount, offset)
                        .stream()
                        .map(this::toSummary)
                        .toList();

        return new AuctionListResponse(
                pageItems,
                toEpochMilli(serverTime),
                toEpochMilli(asOf),
                currentPage,
                totalPages,
                totalCount
        );
    }

    private List<AuctionListRow> findPage(
            AuctionListSearchCondition condition,
            int currentPage,
            int size,
            long totalCount,
            long offset
    ) {
        return switch (condition.sort()) {
            case PRICE_LOW, PRICE_HIGH -> auctionPricePageQuery.findPage(
                    condition,
                    currentPage,
                    size,
                    totalCount
            );
            case RECOMMENDED, DEADLINE, LATEST ->
                    auctionListQueryRepository.findPage(condition, offset, size);
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

    private int totalPages(long totalCount, int size) {
        long calculated = Math.max(FIRST_PAGE, Math.ceilDiv(totalCount, size));
        return (int) Math.min(calculated, Integer.MAX_VALUE);
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(SERVICE_ZONE).toInstant().toEpochMilli();
    }
}
