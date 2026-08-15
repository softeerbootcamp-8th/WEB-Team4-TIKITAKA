package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionListQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListSearchCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionListCountSnapshotServiceTest {

    private static final LocalDateTime DATABASE_TIME = LocalDateTime.of(2026, 8, 15, 12, 0);

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private AuctionListQueryRepository auctionListQueryRepository;

    private AuctionListCountSnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        snapshotService = new AuctionListCountSnapshotService(
                auctionRepository,
                auctionListQueryRepository
        );
    }

    @Test
    void 동일한_DB_시각과_canonical_조건으로_UP_DOWN_count를_조회하고_ALL을_합산한다() {
        AuctionListSearchCondition upCondition = condition(AuctionType.UP);
        AuctionListSearchCondition downCondition = condition(AuctionType.DOWN);
        when(auctionRepository.currentDatabaseTime()).thenReturn(DATABASE_TIME);
        when(auctionListQueryRepository.count(upCondition)).thenReturn(12L);
        when(auctionListQueryRepository.count(downCondition)).thenReturn(18L);

        AuctionListCounts counts = snapshotService.capture();

        assertThat(counts).isEqualTo(new AuctionListCounts(30L, 12L, 18L));
        assertThat(upCondition.sort()).isEqualTo(AuctionSort.RECOMMENDED);
        assertThat(downCondition.sort()).isEqualTo(AuctionSort.RECOMMENDED);
        assertThat(upCondition.keyword()).isNull();
        assertThat(downCondition.keyword()).isNull();
        assertThat(upCondition.asOf()).isEqualTo(DATABASE_TIME);
        assertThat(downCondition.asOf()).isEqualTo(DATABASE_TIME);

        InOrder inOrder = inOrder(auctionRepository, auctionListQueryRepository);
        inOrder.verify(auctionRepository).currentDatabaseTime();
        inOrder.verify(auctionListQueryRepository).count(upCondition);
        inOrder.verify(auctionListQueryRepository).count(downCondition);
    }

    @Test
    void UP과_DOWN_합계가_long을_넘으면_예외를_발생시킨다() {
        when(auctionRepository.currentDatabaseTime()).thenReturn(DATABASE_TIME);
        when(auctionListQueryRepository.count(condition(AuctionType.UP)))
                .thenReturn(Long.MAX_VALUE);
        when(auctionListQueryRepository.count(condition(AuctionType.DOWN))).thenReturn(1L);

        assertThatThrownBy(snapshotService::capture)
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void count는_음수일_수_없다() {
        assertThatThrownBy(() -> new AuctionListCounts(0L, -1L, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AuctionListSearchCondition condition(AuctionType auctionType) {
        return new AuctionListSearchCondition(
                auctionType,
                AuctionSort.RECOMMENDED,
                null,
                DATABASE_TIME
        );
    }
}
