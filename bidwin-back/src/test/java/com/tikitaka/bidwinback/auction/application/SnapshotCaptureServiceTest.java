package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionListStatusFilter;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListSearchCondition;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapshotCaptureServiceTest {

    @Mock
    private AuctionPricePageQuery auctionPricePageQuery;

    @Test
    void 같은_세대시각으로_LOW_HIGH_각각_최대_1600건을_캡처한다() {
        LocalDateTime generationAt = LocalDateTime.of(2026, 8, 18, 12, 0, 30);
        SnapshotBuildKey key = SnapshotBuildKey.exact(generationAt);
        AuctionListSearchCondition lowCondition = condition(AuctionSort.PRICE_LOW, generationAt);
        AuctionListSearchCondition highCondition = condition(AuctionSort.PRICE_HIGH, generationAt);
        List<AuctionPriceSnapshot> low = List.of(snapshot(1L, 100L));
        List<AuctionPriceSnapshot> high = List.of(snapshot(1L, 100L));
        when(auctionPricePageQuery.findSnapshots(
                lowCondition,
                DownPriceSnapshot.MAX_ENTRIES_PER_SORT
        )).thenReturn(low);
        when(auctionPricePageQuery.findSnapshots(
                highCondition,
                DownPriceSnapshot.MAX_ENTRIES_PER_SORT
        )).thenReturn(high);

        DownPriceSnapshot snapshot = new SnapshotCaptureService(auctionPricePageQuery)
                .capture(key);

        assertThat(snapshot.generationAt()).isEqualTo(generationAt);
        assertThat(snapshot.priceLow()).containsExactlyElementsOf(low);
        assertThat(snapshot.priceHigh()).containsExactlyElementsOf(high);
    }

    private AuctionListSearchCondition condition(
            AuctionSort sort,
            LocalDateTime generationAt
    ) {
        return new AuctionListSearchCondition(
                AuctionType.DOWN,
                sort,
                null,
                AuctionListStatusFilter.ACTIVE,
                null,
                generationAt
        );
    }

    private AuctionPriceSnapshot snapshot(long auctionId, long price) {
        return new AuctionPriceSnapshot(auctionId, price, price);
    }
}
