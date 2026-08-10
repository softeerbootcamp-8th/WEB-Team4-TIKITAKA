package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionListQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListSearchCondition;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceCursor;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import com.tikitaka.bidwinback.auction.domain.repository.dto.DownAuctionPriceCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionPricePageQueryTest {

    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 8, 10, 12, 0);

    @Mock
    private AuctionListQueryRepository auctionListQueryRepository;

    private AuctionPricePageQuery auctionPricePageQuery;

    @BeforeEach
    void setUp() {
        auctionPricePageQuery = new AuctionPricePageQuery(auctionListQueryRepository);
        when(auctionListQueryRepository.findRowsByPriceSnapshots(
                anyList(),
                any(LocalDateTime.class)
        ))
                .thenReturn(List.of());
    }

    @Test
    void 하향_낮은가격_top_k가_남은_최저가보다_낮으면_첫_배치에서_멈춘다() {
        // given
        AuctionListSearchCondition condition = downCondition(AuctionSort.PRICE_LOW);
        List<DownAuctionPriceCandidate> firstBatch = IntStream.rangeClosed(1, 1_000)
                .mapToObj(price -> downCandidate(price, price, price))
                .toList();
        when(auctionListQueryRepository.findDownPriceCandidates(
                eq(condition),
                isNull(),
                eq(1_000)
        )).thenReturn(firstBatch);

        // when
        auctionPricePageQuery.findPage(condition, 1, 16, 2_000);

        // then
        verify(auctionListQueryRepository, times(1)).findDownPriceCandidates(
                eq(condition),
                nullable(AuctionPriceCursor.class),
                eq(1_000)
        );
    }

    @Test
    void 하향_낮은가격_top_k를_확정하지_못하면_다음_커서_배치를_반영한다() {
        // given
        AuctionListSearchCondition condition = downCondition(AuctionSort.PRICE_LOW);
        List<DownAuctionPriceCandidate> firstBatch = IntStream.rangeClosed(1, 1_000)
                .mapToObj(price -> downCandidate(price, price, 10_000L))
                .toList();
        List<DownAuctionPriceCandidate> secondBatch = List.of(
                downCandidate(1_001L, 1_001L, 1_001L),
                downCandidate(1_002L, 1_002L, 1_002L)
        );
        when(auctionListQueryRepository.findDownPriceCandidates(
                eq(condition),
                isNull(),
                eq(1_000)
        )).thenReturn(firstBatch);
        when(auctionListQueryRepository.findDownPriceCandidates(
                eq(condition),
                eq(new AuctionPriceCursor(1_000L, 1_000L)),
                eq(1_000)
        )).thenReturn(secondBatch);

        // when
        auctionPricePageQuery.findPage(condition, 1, 2, 1_002);

        // then
        assertThat(capturedSnapshots())
                .extracting(AuctionPriceSnapshot::auctionId)
                .containsExactly(1_001L, 1_002L);
    }

    @Test
    void top_k_현재가와_하향_가격경계가_같으면_다음_배치를_확인한다() {
        // given
        AuctionListSearchCondition condition = downCondition(AuctionSort.PRICE_LOW);
        List<DownAuctionPriceCandidate> firstBatch = IntStream.range(0, 1_000)
                .mapToObj(index -> downCandidate(2_000L - index, 1_000L, 1_000L))
                .toList();
        AuctionPriceCursor cursor = new AuctionPriceCursor(1_000L, 1_001L);
        when(auctionListQueryRepository.findDownPriceCandidates(
                eq(condition),
                isNull(),
                eq(1_000)
        )).thenReturn(firstBatch);
        when(auctionListQueryRepository.findDownPriceCandidates(
                eq(condition),
                eq(cursor),
                eq(1_000)
        )).thenReturn(List.of(downCandidate(1_000L, 1_000L, 1_000L)));

        // when
        auctionPricePageQuery.findPage(condition, 1, 1, 1_001);

        // then
        verify(auctionListQueryRepository).findDownPriceCandidates(
                eq(condition),
                eq(cursor),
                eq(1_000)
        );
    }

    @Test
    void 하향_높은가격순은_시작가를_상한으로_현재가_top_k를_고른다() {
        // given
        AuctionListSearchCondition condition = downCondition(AuctionSort.PRICE_HIGH);
        List<DownAuctionPriceCandidate> candidates = List.of(
                downCandidate(1L, 0L, 1_000L, 100L, 10L, 90L),
                downCandidate(2L, 0L, 900L, 100L, 10L, 10L),
                downCandidate(3L, 0L, 800L, 100L, 10L, 10L)
        );
        when(auctionListQueryRepository.findDownPriceCandidates(
                eq(condition),
                isNull(),
                eq(1_000)
        )).thenReturn(candidates);

        // when
        auctionPricePageQuery.findPage(condition, 1, 2, 3);

        // then
        assertThat(capturedSnapshots())
                .extracting(AuctionPriceSnapshot::auctionId)
                .containsExactly(2L, 3L);
    }

    @Test
    void 하향_높은가격_top_k를_확정하지_못하면_시작가_커서로_이어_조회한다() {
        // given
        AuctionListSearchCondition condition = downCondition(AuctionSort.PRICE_HIGH);
        List<DownAuctionPriceCandidate> firstBatch = IntStream.range(0, 1_000)
                .mapToObj(index -> {
                    long startPrice = 2_000L - index;
                    return downCandidate(
                            startPrice,
                            0L,
                            startPrice,
                            startPrice - 100L,
                            1L,
                            1L
                    );
                })
                .toList();
        AuctionPriceCursor cursor = new AuctionPriceCursor(1_001L, 1_001L);
        when(auctionListQueryRepository.findDownPriceCandidates(
                eq(condition),
                isNull(),
                eq(1_000)
        )).thenReturn(firstBatch);
        when(auctionListQueryRepository.findDownPriceCandidates(
                eq(condition),
                eq(cursor),
                eq(1_000)
        )).thenReturn(List.of(downCandidate(1_000L, 0L, 1_000L)));

        // when
        auctionPricePageQuery.findPage(condition, 1, 1, 1_001);

        // then
        verify(auctionListQueryRepository).findDownPriceCandidates(
                eq(condition),
                eq(cursor),
                eq(1_000)
        );
    }

    @Test
    void 상향_높은가격순은_current_price_스냅샷을_추가계산없이_사용한다() {
        // given
        AuctionListSearchCondition condition = upCondition(AuctionSort.PRICE_HIGH);
        when(auctionListQueryRepository.findUpPriceSnapshots(condition, 2))
                .thenReturn(List.of(
                        new AuctionPriceSnapshot(2L, 800L),
                        new AuctionPriceSnapshot(3L, 700L)
                ));

        // when
        auctionPricePageQuery.findPage(condition, 1, 2, 3);

        // then
        assertThat(capturedSnapshots())
                .extracting(AuctionPriceSnapshot::currentPrice)
                .containsExactly(800L, 700L);
    }

    @Test
    void 상향_낮은가격순도_current_price로_판단하고_하향_top_k를_조회하지_않는다() {
        // given
        AuctionListSearchCondition condition = upCondition(AuctionSort.PRICE_LOW);
        when(auctionListQueryRepository.findUpPriceSnapshots(condition, 2))
                .thenReturn(List.of(
                        new AuctionPriceSnapshot(1L, 100L),
                        new AuctionPriceSnapshot(2L, 200L)
                ));

        // when
        auctionPricePageQuery.findPage(condition, 1, 2, 2);

        // then
        assertThat(capturedSnapshots())
                .extracting(AuctionPriceSnapshot::currentPrice)
                .containsExactly(100L, 200L);
        verify(auctionListQueryRepository, never()).findDownPriceCandidates(
                any(),
                nullable(AuctionPriceCursor.class),
                eq(1_000)
        );
    }

    @Test
    void 상향_current_price와_하향_top_k를_합쳐_전체_가격순을_만든다() {
        // given
        AuctionListSearchCondition condition = condition(null, AuctionSort.PRICE_HIGH);
        when(auctionListQueryRepository.findUpPriceSnapshots(condition, 2))
                .thenReturn(List.of(
                        new AuctionPriceSnapshot(1L, 500L),
                        new AuctionPriceSnapshot(2L, 300L)
                ));
        when(auctionListQueryRepository.findDownPriceCandidates(
                eq(condition),
                isNull(),
                eq(1_000)
        )).thenReturn(List.of(
                downCandidate(3L, 0L, 400L),
                downCandidate(4L, 0L, 200L)
        ));

        // when
        auctionPricePageQuery.findPage(condition, 1, 2, 4);

        // then
        assertThat(capturedSnapshots())
                .extracting(AuctionPriceSnapshot::auctionId)
                .containsExactly(1L, 3L);
    }

    @Test
    void N페이지는_N곱하기_페이지크기_top_k에서_해당_페이지만_조회한다() {
        // given
        AuctionListSearchCondition condition = downCondition(AuctionSort.PRICE_LOW);
        List<DownAuctionPriceCandidate> candidates = IntStream.rangeClosed(1, 40)
                .mapToObj(price -> downCandidate(price, price, price))
                .toList();
        when(auctionListQueryRepository.findDownPriceCandidates(
                eq(condition),
                isNull(),
                eq(1_000)
        )).thenReturn(candidates);

        // when
        auctionPricePageQuery.findPage(condition, 2, 16, 40);

        // then
        assertThat(capturedSnapshots())
                .extracting(AuctionPriceSnapshot::auctionId)
                .containsExactlyElementsOf(
                        IntStream.rangeClosed(17, 32).mapToObj(Long::valueOf).toList()
                );
    }

    @Test
    void 하향_경매_현재가는_조회_asOf를_기준으로_계산한다() {
        // given
        AuctionListSearchCondition condition = downCondition(AuctionSort.PRICE_LOW);
        DownAuctionPriceCandidate downCandidate = downCandidate(
                1L,
                40_000L,
                100_000L,
                10_000L,
                10L,
                20L
        );
        when(auctionListQueryRepository.findDownPriceCandidates(
                eq(condition),
                isNull(),
                eq(1_000)
        )).thenReturn(List.of(downCandidate));

        // when
        auctionPricePageQuery.findPage(condition, 1, 1, 1);

        // then
        assertThat(capturedSnapshots())
                .extracting(AuctionPriceSnapshot::currentPrice)
                .containsExactly(80_000L);
    }

    private List<AuctionPriceSnapshot> capturedSnapshots() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuctionPriceSnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(auctionListQueryRepository).findRowsByPriceSnapshots(
                captor.capture(),
                eq(AS_OF)
        );
        return captor.getValue();
    }

    private AuctionListSearchCondition downCondition(AuctionSort sort) {
        return condition(AuctionType.DOWN, sort);
    }

    private AuctionListSearchCondition upCondition(AuctionSort sort) {
        return condition(AuctionType.UP, sort);
    }

    private AuctionListSearchCondition condition(AuctionType type, AuctionSort sort) {
        return new AuctionListSearchCondition(type, sort, null, AS_OF);
    }

    private DownAuctionPriceCandidate downCandidate(
            long id,
            long minimumPrice,
            long startPrice
    ) {
        return downCandidate(id, minimumPrice, startPrice, 1L, 1L, 0L);
    }

    private DownAuctionPriceCandidate downCandidate(
            long id,
            long minimumPrice,
            long startPrice,
            long dropPrice,
            long priceDropInterval,
            long elapsedMinutes
    ) {
        return new DownAuctionPriceCandidate(
                id,
                startPrice,
                minimumPrice,
                AS_OF.minusMinutes(elapsedMinutes),
                dropPrice,
                priceDropInterval
        );
    }
}
