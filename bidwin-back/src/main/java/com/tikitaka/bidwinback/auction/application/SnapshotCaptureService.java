package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionListStatusFilter;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListSearchCondition;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SnapshotCaptureService {

    private final AuctionPricePageQuery auctionPricePageQuery;

    @Transactional(readOnly = true)
    public DownPriceSnapshot capture(SnapshotBuildKey key) {
        List<AuctionPriceSnapshot> priceLow = auctionPricePageQuery.findSnapshots(
                condition(AuctionSort.PRICE_LOW, key),
                DownPriceSnapshot.MAX_ENTRIES_PER_SORT
        );
        List<AuctionPriceSnapshot> priceHigh = auctionPricePageQuery.findSnapshots(
                condition(AuctionSort.PRICE_HIGH, key),
                DownPriceSnapshot.MAX_ENTRIES_PER_SORT
        );
        return new DownPriceSnapshot(key.generationAt(), priceLow, priceHigh);
    }

    private AuctionListSearchCondition condition(
            AuctionSort sort,
            SnapshotBuildKey key
    ) {
        return new AuctionListSearchCondition(
                AuctionType.DOWN,
                sort,
                null,
                AuctionListStatusFilter.ACTIVE,
                null,
                key.generationAt()
        );
    }
}
