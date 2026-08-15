package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionListQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListRow;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListSearchCondition;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuctionListDbQuery {

    private static final int FIRST_PAGE = 1;

    private final AuctionRepository auctionRepository;
    private final AuctionListQueryRepository auctionListQueryRepository;
    private final AuctionPricePageQuery auctionPricePageQuery;

    @Transactional(readOnly = true)
    public SnapshotPage assembleSnapshotPage(
            List<AuctionPriceSnapshot> snapshots,
            LocalDateTime snapshotAt
    ) {
        LocalDateTime serverTime = auctionRepository.currentDatabaseTime();
        List<AuctionListRow> rows = auctionListQueryRepository
                .findDownRowsByPriceSnapshots(snapshots, snapshotAt);
        return new SnapshotPage(serverTime, rows);
    }

    @Transactional(readOnly = true)
    public DbPage findPage(AuctionListQuery query, int size) {
        LocalDateTime serverTime = auctionRepository.currentDatabaseTime();
        LocalDateTime asOf = query.sort() == AuctionSort.RECOMMENDED
                ? serverTime
                : query.asOf() != null ? query.asOf() : serverTime;

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

        List<AuctionListRow> rows = totalCount == 0
                ? List.of()
                : findPage(condition, currentPage, size, totalCount, offset);
        return new DbPage(
                serverTime,
                asOf,
                totalCount,
                currentPage,
                totalPages,
                rows
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

    private int totalPages(long totalCount, int size) {
        long calculated = Math.max(FIRST_PAGE, Math.ceilDiv(totalCount, size));
        return (int) Math.min(calculated, Integer.MAX_VALUE);
    }

    public record SnapshotPage(
            LocalDateTime serverTime,
            List<AuctionListRow> rows
    ) {
    }

    public record DbPage(
            LocalDateTime serverTime,
            LocalDateTime asOf,
            long totalCount,
            int currentPage,
            int totalPages,
            List<AuctionListRow> rows
    ) {
    }
}
