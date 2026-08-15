package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionListQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListSearchCondition;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DownPriceSnapshotServiceTest {

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private AuctionListQueryRepository auctionListQueryRepository;

    @Mock
    private AuctionPricePageQuery auctionPricePageQuery;

    private DownPriceSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new DownPriceSnapshotService(
                auctionRepository,
                auctionListQueryRepository,
                auctionPricePageQuery
        );
    }

    @Test
    void DB_시각을_밀리초로_절삭해_같은_세대의_낮은순과_높은순을_만든다() {
        LocalDateTime databaseTime = LocalDateTime.of(2026, 8, 15, 10, 0, 0, 123_456_000);
        LocalDateTime snapshotAt = LocalDateTime.of(2026, 8, 15, 10, 0, 0, 123_000_000);
        AuctionListSearchCondition lowCondition = condition(AuctionSort.PRICE_LOW, snapshotAt);
        AuctionListSearchCondition highCondition = condition(AuctionSort.PRICE_HIGH, snapshotAt);
        List<AuctionPriceSnapshot> low = List.of(
                new AuctionPriceSnapshot(2L, 100L),
                new AuctionPriceSnapshot(1L, 200L)
        );
        List<AuctionPriceSnapshot> high = List.of(
                new AuctionPriceSnapshot(1L, 200L),
                new AuctionPriceSnapshot(2L, 100L)
        );
        when(auctionRepository.currentDatabaseTime()).thenReturn(databaseTime);
        when(auctionListQueryRepository.count(lowCondition)).thenReturn(2L);
        when(auctionPricePageQuery.findSnapshots(lowCondition, 2)).thenReturn(low);
        when(auctionPricePageQuery.findSnapshots(highCondition, 2)).thenReturn(high);

        DownPriceSnapshot snapshot = service.capture();

        assertThat(snapshot.snapshotAt()).isEqualTo(snapshotAt);
        assertThat(snapshot.totalCount()).isEqualTo(2L);
        assertThat(snapshot.priceLow()).containsExactlyElementsOf(low);
        assertThat(snapshot.priceHigh()).containsExactlyElementsOf(high);
    }

    @Test
    void 세대당_각_정렬을_최대_1600개까지만_만든다() {
        LocalDateTime snapshotAt = LocalDateTime.of(2026, 8, 15, 10, 0);
        AuctionListSearchCondition lowCondition = condition(AuctionSort.PRICE_LOW, snapshotAt);
        AuctionListSearchCondition highCondition = condition(AuctionSort.PRICE_HIGH, snapshotAt);
        List<AuctionPriceSnapshot> snapshots = java.util.stream.LongStream
                .rangeClosed(1, DownPriceSnapshot.MAX_ENTRIES)
                .mapToObj(id -> new AuctionPriceSnapshot(id, id))
                .toList();
        when(auctionRepository.currentDatabaseTime()).thenReturn(snapshotAt);
        when(auctionListQueryRepository.count(lowCondition)).thenReturn(2_000L);
        when(auctionPricePageQuery.findSnapshots(lowCondition, DownPriceSnapshot.MAX_ENTRIES))
                .thenReturn(snapshots);
        when(auctionPricePageQuery.findSnapshots(highCondition, DownPriceSnapshot.MAX_ENTRIES))
                .thenReturn(snapshots);

        DownPriceSnapshot snapshot = service.capture();

        assertThat(snapshot.totalCount()).isEqualTo(2_000L);
        assertThat(snapshot.priceLow()).hasSize(DownPriceSnapshot.MAX_ENTRIES);
        assertThat(snapshot.priceHigh()).hasSize(DownPriceSnapshot.MAX_ENTRIES);
    }

    @Test
    void 진행중인_하향_경매가_없으면_Top_K를_계산하지_않는다() {
        LocalDateTime snapshotAt = LocalDateTime.of(2026, 8, 15, 10, 0);
        AuctionListSearchCondition condition = condition(AuctionSort.PRICE_LOW, snapshotAt);
        when(auctionRepository.currentDatabaseTime()).thenReturn(snapshotAt);
        when(auctionListQueryRepository.count(condition)).thenReturn(0L);

        DownPriceSnapshot snapshot = service.capture();

        assertThat(snapshot.priceLow()).isEmpty();
        assertThat(snapshot.priceHigh()).isEmpty();
        verify(auctionPricePageQuery, never()).findSnapshots(any(), anyInt());
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
