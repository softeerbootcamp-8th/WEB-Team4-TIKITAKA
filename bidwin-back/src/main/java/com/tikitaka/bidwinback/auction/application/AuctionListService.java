package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListRow;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuctionListService {

    private static final int FIRST_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 16;
    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final AuctionListDbQuery auctionListDbQuery;
    private final DownPriceSnapshotCache downPriceSnapshotCache;
    private final ImageUrlResolver imageUrlResolver;

    public AuctionListResponse getList(AuctionListQuery query) {
        int size = normalizedSize(query.size());

        Optional<AuctionListResponse> snapshotResponse = findDownPriceSnapshotList(
                query,
                size
        );
        if (snapshotResponse.isPresent()) {
            return snapshotResponse.get();
        }

        return toResponse(auctionListDbQuery.findPage(query, size));
    }

    private Optional<AuctionListResponse> findDownPriceSnapshotList(
            AuctionListQuery query,
            int size
    ) {
        if (query.auctionType() != AuctionType.DOWN
                || (query.sort() != AuctionSort.PRICE_LOW
                && query.sort() != AuctionSort.PRICE_HIGH)
                || query.keyword() != null) {
            return Optional.empty();
        }

        Optional<DownPriceSnapshotCache.Metadata> metadata = query.asOf() != null
                ? downPriceSnapshotCache.findLatestAtNotAfter(query.asOf())
                : downPriceSnapshotCache.findLatest();
        return metadata.flatMap(value -> toSnapshotResponse(query, size, value));
    }

    private Optional<AuctionListResponse> toSnapshotResponse(
            AuctionListQuery query,
            int size,
            DownPriceSnapshotCache.Metadata metadata
    ) {
        int totalPages = totalPages(metadata.totalCount(), size);
        int currentPage = Math.min(Math.max(FIRST_PAGE, query.page()), totalPages);
        long offset = (long) (currentPage - FIRST_PAGE) * size;

        Optional<List<AuctionPriceSnapshot>> snapshots =
                downPriceSnapshotCache.findPage(
                        metadata,
                        query.sort(),
                        offset,
                        size
                );
        if (snapshots.isEmpty()) {
            return Optional.empty();
        }

        AuctionListDbQuery.SnapshotPage page = auctionListDbQuery
                .assembleSnapshotPage(snapshots.get(), metadata.snapshotAt());
        // AuctionListRow는 @QueryProjection 레코드라 지연 로딩 프록시 없이
        // 트랜잭션 밖에서 안전하게 응답으로 매핑할 수 있다.
        List<AuctionSummaryResponse> pageItems = page.rows()
                .stream()
                .map(this::toSummary)
                .toList();
        return Optional.of(new AuctionListResponse(
                pageItems,
                toEpochMilli(page.serverTime()),
                toEpochMilli(metadata.snapshotAt()),
                currentPage,
                totalPages,
                metadata.totalCount()
        ));
    }

    private AuctionListResponse toResponse(AuctionListDbQuery.DbPage page) {
        // AuctionListRow는 @QueryProjection 레코드라 지연 로딩 프록시 없이
        // 트랜잭션 밖에서 안전하게 응답으로 매핑할 수 있다.
        List<AuctionSummaryResponse> pageItems = page.rows().stream()
                .map(this::toSummary)
                .toList();
        return new AuctionListResponse(
                pageItems,
                toEpochMilli(page.serverTime()),
                toEpochMilli(page.asOf()),
                page.currentPage(),
                page.totalPages(),
                page.totalCount()
        );
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
