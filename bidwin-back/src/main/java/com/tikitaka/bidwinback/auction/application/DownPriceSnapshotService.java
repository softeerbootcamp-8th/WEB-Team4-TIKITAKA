package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionListQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListSearchCondition;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DownPriceSnapshotService {

    private final AuctionRepository auctionRepository;
    private final AuctionListQueryRepository auctionListQueryRepository;
    private final AuctionPricePageQuery auctionPricePageQuery;

    @Transactional(readOnly = true)
    public DownPriceSnapshot capture() {
        LocalDateTime snapshotAt = auctionRepository.currentDatabaseTime()
                .truncatedTo(ChronoUnit.MILLIS);
        AuctionListSearchCondition priceLowCondition = condition(
                AuctionSort.PRICE_LOW,
                snapshotAt
        );
        long totalCount = auctionListQueryRepository.count(priceLowCondition);
        int snapshotSize = (int) Math.min(totalCount, DownPriceSnapshot.MAX_ENTRIES);

        if (snapshotSize == 0) {
            return new DownPriceSnapshot(snapshotAt, totalCount, List.of(), List.of());
        }

        List<AuctionPriceSnapshot> priceLow = auctionPricePageQuery.findSnapshots(
                priceLowCondition,
                snapshotSize
        );
        List<AuctionPriceSnapshot> priceHigh = auctionPricePageQuery.findSnapshots(
                condition(AuctionSort.PRICE_HIGH, snapshotAt),
                snapshotSize
        );
        return new DownPriceSnapshot(snapshotAt, totalCount, priceLow, priceHigh);
    }

    private AuctionListSearchCondition condition(
            AuctionSort sort,
            LocalDateTime snapshotAt
    ) {
        return new AuctionListSearchCondition(
                AuctionType.DOWN,
                sort,
                null,
                snapshotAt
        );
    }
}
