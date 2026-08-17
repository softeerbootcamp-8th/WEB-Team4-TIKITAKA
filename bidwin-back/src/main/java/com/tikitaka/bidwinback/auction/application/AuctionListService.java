package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionListStatusFilter;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
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
    static final int MAX_LIST_PAGES = 100;
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
        LocalDateTime asOf = query.sort() == AuctionSort.RECOMMENDED
                ? serverTime
                : query.asOf() != null ? query.asOf() : serverTime;
        int size = normalizedSize(query.size());

        AuctionListSearchCondition condition = new AuctionListSearchCondition(
                query.auctionType(),
                query.sort(),
                query.keyword(),
                query.status() != null ? query.status() : AuctionListStatusFilter.ACTIVE,
                query.category(),
                asOf
        );
        int currentPage = normalizedPage(query.page());
        long offset = (long) (currentPage - FIRST_PAGE) * size;
        // 정확한 COUNT를 생략하므로 totalCount는 클라이언트가 조회할 수 있는 상한이다.
        long totalCount = (long) MAX_LIST_PAGES * size;

        List<AuctionSummaryResponse> pageItems = findPage(
                condition,
                currentPage,
                size,
                totalCount,
                offset
        ).stream()
                .map(this::toSummary)
                .toList();

        return new AuctionListResponse(
                pageItems,
                toEpochMilli(serverTime),
                toEpochMilli(asOf),
                currentPage,
                MAX_LIST_PAGES,
                totalCount
        );
    }

    private List<AuctionListRow> findPage(
            AuctionListSearchCondition condition,
            int currentPage,
            int size,
            long candidateCountLimit,
            long offset
    ) {
        return switch (condition.sort()) {
            case PRICE_LOW, PRICE_HIGH -> auctionPricePageQuery.findPage(
                    condition,
                    currentPage,
                    size,
                    candidateCountLimit
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

    private int normalizedPage(int requestedPage) {
        return Math.min(Math.max(FIRST_PAGE, requestedPage), MAX_LIST_PAGES);
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(SERVICE_ZONE).toInstant().toEpochMilli();
    }
}
