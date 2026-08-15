package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionListQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListRow;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListSearchCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalLong;

@Component
@RequiredArgsConstructor
public class AuctionListDbQuery {

    private static final int FIRST_PAGE = 1;

    private final AuctionRepository auctionRepository;
    private final AuctionListQueryRepository auctionListQueryRepository;
    private final AuctionPricePageQuery auctionPricePageQuery;

    @Transactional(readOnly = true)
    public DbPage findPage(
            AuctionListQuery query,
            int size,
            OptionalLong cachedTotalCount
    ) {
        LocalDateTime serverTime = auctionRepository.currentDatabaseTime();
        LocalDateTime asOf = query.sort() == AuctionSort.RECOMMENDED
                ? serverTime
                : query.asOf() != null ? query.asOf() : serverTime;

        // 상태·카테고리는 캐시 적격성만 판단하고, 실제 조회 반영은 별도 작업에서 다룬다.
        AuctionListSearchCondition condition = new AuctionListSearchCondition(
                query.auctionType(),
                query.sort(),
                query.keyword(),
                asOf
        );
        long totalCount = cachedTotalCount.isPresent()
                ? cachedTotalCount.getAsLong()
                : auctionListQueryRepository.count(condition);
        int totalPages = totalPages(totalCount, size);
        int currentPage = Math.min(Math.max(FIRST_PAGE, query.page()), totalPages);
        long offset = (long) (currentPage - FIRST_PAGE) * size;

        List<AuctionListRow> pageItems = totalCount == 0
                ? List.of()
                : findRows(condition, currentPage, size, totalCount, offset);

        return new DbPage(
                pageItems,
                serverTime,
                asOf,
                currentPage,
                totalPages,
                totalCount
        );
    }

    private List<AuctionListRow> findRows(
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

    public record DbPage(
            List<AuctionListRow> items,
            LocalDateTime serverTime,
            LocalDateTime asOf,
            int currentPage,
            int totalPages,
            long totalCount
    ) {
    }
}
