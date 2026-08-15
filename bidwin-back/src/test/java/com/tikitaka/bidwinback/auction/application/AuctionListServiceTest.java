package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionListQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListRow;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListSearchCondition;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionListResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionSummaryResponse;
import com.tikitaka.bidwinback.global.storage.ImageUrlResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionListServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 8, 1, 12, 0);
    private static final LocalDateTime SERVER_TIME = AS_OF.plusMinutes(1);

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private AuctionListQueryRepository auctionListQueryRepository;

    @Mock
    private AuctionPricePageQuery auctionPricePageQuery;

    @Mock
    private ImageUrlResolver imageUrlResolver;

    @Mock
    private AuctionListCountCache countCache;

    private AuctionListService auctionListService;

    @BeforeEach
    void setUp() {
        AuctionListDbQuery auctionListDbQuery = new AuctionListDbQuery(
                auctionRepository,
                auctionListQueryRepository,
                auctionPricePageQuery
        );
        auctionListService = new AuctionListService(
                countCache,
                auctionListDbQuery,
                imageUrlResolver
        );
        when(auctionRepository.currentDatabaseTime()).thenReturn(SERVER_TIME);
    }

    @Test
    void projection을_UP_DOWN_경매_응답으로_매핑하고_썸네일_URL을_변환한다() {
        AuctionListRow up = upRow(1L, "up-thumbnail", 260_000L, 4L);
        AuctionListRow down = downRow(2L, "down-thumbnail", 170_000L);
        when(auctionListQueryRepository.count(any())).thenReturn(2L);
        when(auctionListQueryRepository.findPage(any(), eq(0L), eq(16)))
                .thenReturn(List.of(up, down));
        when(imageUrlResolver.resolve("up-thumbnail")).thenReturn("https://cdn/up.jpg");
        when(imageUrlResolver.resolve("down-thumbnail")).thenReturn("https://cdn/down.jpg");

        AuctionListResponse response = auctionListService.getList(
                query(null, AuctionSort.LATEST, null, 1, 16)
        );

        AuctionSummaryResponse upSummary = response.items().get(0);
        assertThat(upSummary.auctionId()).isEqualTo(1L);
        assertThat(upSummary.auctionType()).isEqualTo(AuctionType.UP);
        assertThat(upSummary.thumbnailUrl()).isEqualTo("https://cdn/up.jpg");
        assertThat(upSummary.currentPrice()).isEqualTo(260_000L);
        assertThat(upSummary.bidCount()).isEqualTo(4L);
        assertThat(upSummary.downPricing()).isNull();

        AuctionSummaryResponse downSummary = response.items().get(1);
        assertThat(downSummary.auctionId()).isEqualTo(2L);
        assertThat(downSummary.auctionType()).isEqualTo(AuctionType.DOWN);
        assertThat(downSummary.thumbnailUrl()).isEqualTo("https://cdn/down.jpg");
        assertThat(downSummary.currentPrice()).isEqualTo(170_000L);
        assertThat(downSummary.downPricing()).isNotNull();
        assertThat(downSummary.downPricing().minimumPrice()).isEqualTo(150_000L);
        assertThat(downSummary.downPricing().dropPrice()).isEqualTo(10_000L);
        assertThat(downSummary.downPricing().priceDropIntervalMs()).isEqualTo(600_000L);
        assertThat(downSummary.downPricing().startedAt())
                .isEqualTo(toEpochMilli(LocalDateTime.of(2026, 8, 1, 9, 0)));
    }

    @Test
    void DB_서버시각과_명시한_asOf를_응답과_조회조건에_사용한다() {
        when(auctionListQueryRepository.count(any())).thenReturn(0L);

        AuctionListResponse response = auctionListService.getList(
                query(null, AuctionSort.LATEST, null, 1, 16, AS_OF)
        );

        assertThat(response.serverTime()).isEqualTo(toEpochMilli(SERVER_TIME));
        assertThat(response.asOf()).isEqualTo(toEpochMilli(AS_OF));
        verify(auctionListQueryRepository).count(
                eq(new AuctionListSearchCondition(null, AuctionSort.LATEST, null, AS_OF))
        );
    }

    @Test
    void asOf가_없으면_DB_서버시각을_목록_스냅샷으로_사용한다() {
        when(auctionListQueryRepository.count(any())).thenReturn(0L);

        AuctionListResponse response = auctionListService.getList(
                query(null, AuctionSort.LATEST, null, 1, 16, null)
        );

        assertThat(response.serverTime()).isEqualTo(toEpochMilli(SERVER_TIME));
        assertThat(response.asOf()).isEqualTo(toEpochMilli(SERVER_TIME));
        verify(auctionListQueryRepository).count(
                eq(new AuctionListSearchCondition(null, AuctionSort.LATEST, null, SERVER_TIME))
        );
    }

    @Test
    void 추천순은_요청_asOf를_무시하고_DB_현재시각을_사용한다() {
        when(auctionListQueryRepository.count(any())).thenReturn(0L);

        AuctionListResponse response = auctionListService.getList(
                query(null, AuctionSort.RECOMMENDED, null, 1, 16, AS_OF)
        );

        assertThat(response.serverTime()).isEqualTo(toEpochMilli(SERVER_TIME));
        assertThat(response.asOf()).isEqualTo(toEpochMilli(SERVER_TIME));
        verify(auctionListQueryRepository).count(
                eq(new AuctionListSearchCondition(
                        null,
                        AuctionSort.RECOMMENDED,
                        null,
                        SERVER_TIME
                ))
        );
    }

    @Test
    void 전체_개수로_totalPages를_계산하고_페이지_크기만큼_조회한다() {
        when(auctionListQueryRepository.count(any())).thenReturn(3L);
        when(auctionListQueryRepository.findPage(any(), eq(0L), eq(2)))
                .thenReturn(List.of(upRow(1L, null, 100_000L, 0L), upRow(2L, null, 200_000L, 1L)));

        AuctionListResponse response = auctionListService.getList(
                query(null, AuctionSort.LATEST, null, 1, 2)
        );

        assertThat(response.items()).hasSize(2);
        assertThat(response.totalCount()).isEqualTo(3L);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.page()).isEqualTo(1);
    }

    @Test
    void 가격순은_전체_집계_페이지_쿼리_대신_top_k_조회를_사용한다() {
        // given
        AuctionListSearchCondition condition = new AuctionListSearchCondition(
                null,
                AuctionSort.PRICE_LOW,
                null,
                AS_OF
        );
        when(auctionListQueryRepository.count(condition)).thenReturn(2L);
        when(auctionPricePageQuery.findPage(condition, 1, 16, 2L))
                .thenReturn(List.of(upRow(1L, null, 100_000L, 0L)));

        // when
        auctionListService.getList(query(null, AuctionSort.PRICE_LOW, null, 1, 16));

        // then
        verify(auctionPricePageQuery).findPage(condition, 1, 16, 2L);
        verify(auctionListQueryRepository, never()).findPage(any(), anyLong(), anyInt());
    }

    @Test
    void page가_1보다_작으면_첫_페이지와_offset_0으로_보정한다() {
        when(auctionListQueryRepository.count(any())).thenReturn(5L);
        when(auctionListQueryRepository.findPage(any(), eq(0L), eq(2)))
                .thenReturn(List.of(upRow(1L, null, 100_000L, 0L), upRow(2L, null, 200_000L, 1L)));

        AuctionListResponse response = auctionListService.getList(
                query(null, AuctionSort.LATEST, null, 0, 2)
        );

        assertThat(response.page()).isEqualTo(1);
        verify(auctionListQueryRepository).findPage(any(), eq(0L), eq(2));
    }

    @Test
    void page가_마지막_페이지를_초과하면_마지막_페이지와_해당_offset으로_보정한다() {
        when(auctionListQueryRepository.count(any())).thenReturn(5L);
        when(auctionListQueryRepository.findPage(any(), eq(4L), eq(2)))
                .thenReturn(List.of(upRow(5L, null, 100_000L, 0L)));

        AuctionListResponse response = auctionListService.getList(
                query(null, AuctionSort.LATEST, null, 99, 2)
        );

        assertThat(response.page()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(3);
        verify(auctionListQueryRepository).findPage(any(), eq(4L), eq(2));
    }

    @Test
    void size가_0이하면_기본_크기_16을_사용한다() {
        when(auctionListQueryRepository.count(any())).thenReturn(17L);
        when(auctionListQueryRepository.findPage(any(), eq(0L), eq(16)))
                .thenReturn(List.of(upRow(1L, null, 100_000L, 0L)));

        AuctionListResponse response = auctionListService.getList(
                query(null, AuctionSort.LATEST, null, 1, 0)
        );

        assertThat(response.totalPages()).isEqualTo(2);
        verify(auctionListQueryRepository).findPage(any(), eq(0L), eq(16));
    }

    @Test
    void size가_100보다_크면_100으로_제한한다() {
        when(auctionListQueryRepository.count(any())).thenReturn(201L);
        when(auctionListQueryRepository.findPage(any(), eq(100L), eq(100)))
                .thenReturn(List.of(upRow(101L, null, 100_000L, 0L)));

        AuctionListResponse response = auctionListService.getList(
                query(null, AuctionSort.LATEST, null, 2, 200)
        );

        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.page()).isEqualTo(2);
        verify(auctionListQueryRepository).findPage(any(), eq(100L), eq(100));
    }

    @Test
    void 결과가_없으면_페이지_조회는_호출하지_않는다() {
        when(auctionListQueryRepository.count(any())).thenReturn(0L);

        AuctionListResponse response = auctionListService.getList(
                query(null, AuctionSort.LATEST, null, 1, 16)
        );

        assertThat(response.items()).isEmpty();
        assertThat(response.totalCount()).isZero();
        assertThat(response.totalPages()).isEqualTo(1);
        verify(auctionListQueryRepository, never()).findPage(any(), anyLong(), anyInt());
    }

    @ParameterizedTest
    @EnumSource(value = AuctionSort.class, names = {"RECOMMENDED", "DEADLINE", "LATEST"})
    void 필터_없는_ALL_일반정렬은_ALL_count_캐시를_조회한다(AuctionSort sort) {
        when(countCache.find(AuctionListCountScope.ALL)).thenReturn(OptionalLong.of(0L));

        auctionListService.getList(query(null, sort, null, 1, 16));

        verify(countCache).find(AuctionListCountScope.ALL);
        verify(auctionListQueryRepository, never()).count(any());
    }

    @ParameterizedTest
    @EnumSource(AuctionSort.class)
    void 필터_없는_UP_모든_정렬은_UP_count_캐시를_조회한다(AuctionSort sort) {
        when(countCache.find(AuctionListCountScope.UP)).thenReturn(OptionalLong.of(0L));

        auctionListService.getList(query(AuctionType.UP, sort, null, 1, 16));

        verify(countCache).find(AuctionListCountScope.UP);
        verify(auctionListQueryRepository, never()).count(any());
    }

    @ParameterizedTest
    @EnumSource(AuctionSort.class)
    void 필터_없는_DOWN_모든_정렬은_DOWN_count_캐시를_조회한다(AuctionSort sort) {
        when(countCache.find(AuctionListCountScope.DOWN)).thenReturn(OptionalLong.of(0L));

        auctionListService.getList(query(AuctionType.DOWN, sort, null, 1, 16));

        verify(countCache).find(AuctionListCountScope.DOWN);
        verify(auctionListQueryRepository, never()).count(any());
    }

    @Test
    void 같은_경매유형은_정렬과_무관하게_같은_count_캐시를_사용한다() {
        when(countCache.find(AuctionListCountScope.UP)).thenReturn(OptionalLong.of(0L));

        auctionListService.getList(query(AuctionType.UP, AuctionSort.RECOMMENDED, null, 1, 16));
        auctionListService.getList(query(AuctionType.UP, AuctionSort.PRICE_HIGH, null, 1, 16));

        verify(countCache, times(2)).find(AuctionListCountScope.UP);
    }

    @Test
    void keyword가_있으면_count_캐시를_조회하지_않는다() {
        when(auctionListQueryRepository.count(any())).thenReturn(0L);

        auctionListService.getList(query(null, AuctionSort.LATEST, "상품", 1, 16));

        verify(countCache, never()).find(any());
        verify(auctionListQueryRepository).count(any());
    }

    @Test
    void status가_있으면_count_캐시를_조회하지_않고_기존_DB_count를_사용한다() {
        when(auctionListQueryRepository.count(any())).thenReturn(0L);

        auctionListService.getList(query(
                null,
                AuctionSort.LATEST,
                null,
                AuctionListQuery.StatusFilter.ACTIVE,
                List.of(),
                1,
                16,
                AS_OF
        ));

        verify(countCache, never()).find(any());
        verify(auctionListQueryRepository).count(any());
    }

    @Test
    void categories가_있으면_count_캐시를_조회하지_않고_기존_DB_count를_사용한다() {
        when(auctionListQueryRepository.count(any())).thenReturn(0L);

        auctionListService.getList(query(
                null,
                AuctionSort.LATEST,
                null,
                null,
                List.of(AuctionCategory.FOOD),
                1,
                16,
                AS_OF
        ));

        verify(countCache, never()).find(any());
        verify(auctionListQueryRepository).count(any());
    }

    @Test
    void 빈_keyword와_null_categories는_count_캐시_적격이다() {
        when(countCache.find(AuctionListCountScope.ALL)).thenReturn(OptionalLong.of(0L));

        auctionListService.getList(query(
                null,
                AuctionSort.LATEST,
                "   ",
                null,
                null,
                1,
                16,
                AS_OF
        ));

        verify(countCache).find(AuctionListCountScope.ALL);
        verify(auctionListQueryRepository, never()).count(any());
    }

    @Test
    void Redis_miss면_기존_DB_count를_사용한다() {
        when(countCache.find(AuctionListCountScope.ALL)).thenReturn(OptionalLong.empty());
        when(auctionListQueryRepository.count(any())).thenReturn(0L);

        auctionListService.getList(query(null, AuctionSort.LATEST, null, 1, 16));

        verify(auctionListQueryRepository).count(any());
    }

    @Test
    void Redis_조회가_예외여도_기존_DB_count를_사용한다() {
        when(countCache.find(AuctionListCountScope.ALL))
                .thenThrow(new IllegalStateException("redis unavailable"));
        when(auctionListQueryRepository.count(any())).thenReturn(0L);

        auctionListService.getList(query(null, AuctionSort.LATEST, null, 1, 16));

        verify(auctionListQueryRepository).count(any());
    }

    @Test
    void cached_count로_totalCount와_totalPages를_계산하고_DB_count를_생략한다() {
        when(countCache.find(AuctionListCountScope.ALL)).thenReturn(OptionalLong.of(5L));
        when(auctionListQueryRepository.findPage(any(), eq(4L), eq(2)))
                .thenReturn(List.of(upRow(5L, null, 100_000L, 0L)));

        AuctionListResponse response = auctionListService.getList(
                query(null, AuctionSort.LATEST, null, 99, 2)
        );

        assertThat(response.totalCount()).isEqualTo(5L);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.page()).isEqualTo(3);
        verify(auctionListQueryRepository, never()).count(any());
    }

    @Test
    void cached_count가_0이면_DB_count와_목록_행_조회를_생략한다() {
        when(countCache.find(AuctionListCountScope.DOWN)).thenReturn(OptionalLong.of(0L));

        AuctionListResponse response = auctionListService.getList(
                query(AuctionType.DOWN, AuctionSort.LATEST, null, 1, 16)
        );

        assertThat(response.items()).isEmpty();
        assertThat(response.totalCount()).isZero();
        verify(auctionListQueryRepository, never()).count(any());
        verify(auctionListQueryRepository, never()).findPage(any(), anyLong(), anyInt());
    }

    @Test
    void 가격순_cached_count를_기존_Top_K_조회에_전달한다() {
        AuctionListSearchCondition condition = new AuctionListSearchCondition(
                AuctionType.UP,
                AuctionSort.PRICE_LOW,
                null,
                AS_OF
        );
        when(countCache.find(AuctionListCountScope.UP)).thenReturn(OptionalLong.of(3L));
        when(auctionPricePageQuery.findPage(condition, 1, 16, 3L)).thenReturn(List.of());

        auctionListService.getList(query(AuctionType.UP, AuctionSort.PRICE_LOW, null, 1, 16));

        verify(auctionPricePageQuery).findPage(condition, 1, 16, 3L);
        verify(auctionListQueryRepository, never()).count(any());
    }

    @ParameterizedTest
    @EnumSource(value = AuctionSort.class, names = {"PRICE_LOW", "PRICE_HIGH"})
    void ALL_가격순은_count_캐시를_사용하지_않고_기존_DB_경로를_유지한다(AuctionSort sort) {
        AuctionListSearchCondition condition = new AuctionListSearchCondition(
                null,
                sort,
                null,
                AS_OF
        );
        when(auctionListQueryRepository.count(condition)).thenReturn(1L);
        when(auctionPricePageQuery.findPage(condition, 1, 16, 1L)).thenReturn(List.of());

        auctionListService.getList(query(null, sort, null, 1, 16));

        verify(countCache, never()).find(any());
        verify(auctionListQueryRepository).count(condition);
        verify(auctionPricePageQuery).findPage(condition, 1, 16, 1L);
    }

    private AuctionListQuery query(AuctionType type, AuctionSort sort, String keyword, int page, int size) {
        return query(type, sort, keyword, page, size, AS_OF);
    }

    private AuctionListQuery query(
            AuctionType type,
            AuctionSort sort,
            String keyword,
            int page,
            int size,
            LocalDateTime asOf
    ) {
        return query(type, sort, keyword, null, List.of(), page, size, asOf);
    }

    private AuctionListQuery query(
            AuctionType type,
            AuctionSort sort,
            String keyword,
            AuctionListQuery.StatusFilter status,
            List<AuctionCategory> categories,
            int page,
            int size,
            LocalDateTime asOf
    ) {
        return new AuctionListQuery(type, sort, keyword, status, categories, page, size, asOf);
    }

    private AuctionListRow upRow(long id, String thumbnailObjectKey, long currentPrice, long bidCount) {
        return new AuctionListRow(
                id,
                AuctionType.UP,
                "상품" + id,
                "판매자" + id,
                AuctionCategory.HOUSEHOLD,
                thumbnailObjectKey,
                currentPrice,
                200_000L,
                bidCount,
                LocalDateTime.of(2026, 8, 1, 18, 0),
                LocalDateTime.of(2026, 8, 1, 9, 0),
                AuctionStatus.BID_ONGOING,
                id,
                null,
                null,
                null,
                null
        );
    }

    private AuctionListRow downRow(long id, String thumbnailObjectKey, long currentPrice) {
        return new AuctionListRow(
                id,
                AuctionType.DOWN,
                "상품" + id,
                "판매자" + id,
                AuctionCategory.FOOD,
                thumbnailObjectKey,
                currentPrice,
                200_000L,
                0L,
                LocalDateTime.of(2026, 8, 1, 18, 0),
                LocalDateTime.of(2026, 8, 1, 9, 0),
                AuctionStatus.OPEN,
                id,
                150_000L,
                10_000L,
                10L,
                LocalDateTime.of(2026, 8, 1, 9, 0)
        );
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(SEOUL).toInstant().toEpochMilli();
    }
}
