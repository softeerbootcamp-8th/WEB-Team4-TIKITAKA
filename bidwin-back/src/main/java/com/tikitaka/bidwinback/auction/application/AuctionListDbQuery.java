package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionListStatusFilter;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionListQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListRow;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListSearchCondition;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuctionListDbQuery {

    private static final int FIRST_PAGE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final AuctionRepository auctionRepository;
    private final AuctionListQueryRepository auctionListQueryRepository;
    private final AuctionPricePageQuery auctionPricePageQuery;
    private final AuctionSummaryResponseMapper responseMapper;

    @Transactional(readOnly = true)
    public AuctionListResponse findPage(AuctionListQuery query) {
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
        long totalCount = (long) AuctionListService.MAX_LIST_PAGES * size;

        List<AuctionListRow> rows = findPage(
                condition,
                currentPage,
                size,
                totalCount,
                offset
        );
        return new AuctionListResponse(
                rows.stream().map(responseMapper::toSummary).toList(),
                responseMapper.toEpochMilli(serverTime),
                responseMapper.toEpochMilli(asOf),
                currentPage,
                AuctionListService.MAX_LIST_PAGES,
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

    private int normalizedSize(int requestedSize) {
        if (requestedSize <= 0) {
            return AuctionListService.DEFAULT_PAGE_SIZE;
        }
        return Math.min(requestedSize, MAX_PAGE_SIZE);
    }

    private int normalizedPage(int requestedPage) {
        return Math.min(
                Math.max(FIRST_PAGE, requestedPage),
                AuctionListService.MAX_LIST_PAGES
        );
    }
}
