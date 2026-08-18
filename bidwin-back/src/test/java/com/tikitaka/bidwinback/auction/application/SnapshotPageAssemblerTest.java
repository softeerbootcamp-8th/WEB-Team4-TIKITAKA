package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionListQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListRow;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionListResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionSummaryResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapshotPageAssemblerTest {

    @Mock
    private AuctionListQueryRepository auctionListQueryRepository;

    @Mock
    private AuctionSummaryResponseMapper responseMapper;

    @Mock
    private AuctionListRow row;

    @Mock
    private AuctionSummaryResponse summary;

    @Test
    void 선택된_16건만_DOWN_전용_조회하고_reset_응답_계약을_조립한다() {
        LocalDateTime generationAt = LocalDateTime.of(2026, 8, 18, 12, 0);
        LocalDateTime serverTime = generationAt.plusSeconds(10);
        List<AuctionPriceSnapshot> entries = List.of(
                new AuctionPriceSnapshot(1L, 100L, 90L)
        );
        ResolvedSnapshot resolved = new ResolvedSnapshot(
                new SnapshotGenerationPage(generationAt, 1_600, entries),
                serverTime,
                1,
                true,
                SnapshotResetReason.GENERATION_EXPIRED
        );
        AuctionListQuery query = new AuctionListQuery(
                AuctionType.DOWN,
                AuctionSort.PRICE_LOW,
                null,
                null,
                null,
                5,
                16,
                generationAt.minusMinutes(1)
        );
        when(auctionListQueryRepository.findDownRowsByPriceSnapshots(
                entries,
                generationAt
        )).thenReturn(List.of(row));
        when(responseMapper.toSummary(row)).thenReturn(summary);
        when(responseMapper.toEpochMilli(serverTime)).thenReturn(2L);
        when(responseMapper.toEpochMilli(generationAt)).thenReturn(1L);

        AuctionListResponse response = new SnapshotPageAssembler(
                auctionListQueryRepository,
                responseMapper,
                new DownPriceSnapshotMetrics(new SimpleMeterRegistry())
        ).assemble(query, resolved);

        assertThat(response.items()).containsExactly(summary);
        assertThat(response.serverTime()).isEqualTo(2L);
        assertThat(response.asOf()).isEqualTo(1L);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(100);
        assertThat(response.totalCount()).isEqualTo(1_600L);
        assertThat(response.snapshotReset()).isTrue();
        assertThat(response.snapshotResetReason())
                .isEqualTo(SnapshotResetReason.GENERATION_EXPIRED);
        verify(auctionListQueryRepository).findDownRowsByPriceSnapshots(
                entries,
                generationAt
        );
    }
}
