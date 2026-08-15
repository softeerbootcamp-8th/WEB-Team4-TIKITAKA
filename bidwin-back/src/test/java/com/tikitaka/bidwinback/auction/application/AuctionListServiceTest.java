package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionListQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListRow;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListSearchCondition;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionListResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionSummaryResponse;
import com.tikitaka.bidwinback.global.storage.ImageUrlResolver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionListServiceTest {

    private static final String LOOKUP_METRIC = "auction.down.price.snapshot.lookup";
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
    private DownPriceSnapshotCache downPriceSnapshotCache;

    @Mock
    private ImageUrlResolver imageUrlResolver;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ListOperations<String, String> listOperations;

    private AuctionListService auctionListService;

    @BeforeEach
    void setUp() {
        auctionListService = new AuctionListService(
                auctionRepository,
                auctionListQueryRepository,
                auctionPricePageQuery,
                downPriceSnapshotCache,
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
    void 하향_가격순은_Redis_스냅샷_세대의_개수와_순서를_사용한다() {
        LocalDateTime snapshotAt = AS_OF.minusSeconds(30);
        DownPriceSnapshotCache.Metadata metadata =
                new DownPriceSnapshotCache.Metadata(snapshotAt, 2L);
        List<AuctionPriceSnapshot> snapshots = List.of(
                new AuctionPriceSnapshot(2L, 170_000L),
                new AuctionPriceSnapshot(1L, 180_000L)
        );
        when(downPriceSnapshotCache.findLatestAtNotAfter(AS_OF))
                .thenReturn(Optional.of(metadata));
        when(downPriceSnapshotCache.findPage(metadata, AuctionSort.PRICE_LOW, 0L, 16))
                .thenReturn(Optional.of(snapshots));
        when(auctionListQueryRepository.findDownRowsByPriceSnapshots(snapshots, snapshotAt))
                .thenReturn(List.of(
                        downRow(2L, null, 170_000L),
                        downRow(1L, null, 180_000L)
                ));

        AuctionListResponse response = auctionListService.getList(
                query(AuctionType.DOWN, AuctionSort.PRICE_LOW, null, 1, 16)
        );

        assertThat(response.items())
                .extracting(AuctionSummaryResponse::auctionId)
                .containsExactly(2L, 1L);
        assertThat(response.asOf()).isEqualTo(toEpochMilli(snapshotAt));
        assertThat(response.serverTime()).isEqualTo(toEpochMilli(SERVER_TIME));
        assertThat(response.totalCount()).isEqualTo(2L);
        verify(auctionListQueryRepository, never()).count(any());
        verify(auctionPricePageQuery, never()).findPage(any(), anyInt(), anyInt(), anyLong());
        InOrder order = inOrder(
                downPriceSnapshotCache,
                auctionRepository,
                auctionListQueryRepository
        );
        order.verify(downPriceSnapshotCache).findLatestAtNotAfter(AS_OF);
        order.verify(downPriceSnapshotCache)
                .findPage(metadata, AuctionSort.PRICE_LOW, 0L, 16);
        order.verify(auctionRepository).currentDatabaseTime();
        order.verify(auctionListQueryRepository)
                .findDownRowsByPriceSnapshots(snapshots, snapshotAt);
        verify(auctionListQueryRepository, never())
                .findRowsByPriceSnapshots(any(), any());
    }

    @Test
    void 응답_asOf를_다시_요청하면_같은_Redis_세대를_해결한다() {
        LocalDateTime snapshotAt = AS_OF.minusSeconds(30);
        DownPriceSnapshotCache.Metadata metadata =
                new DownPriceSnapshotCache.Metadata(snapshotAt, 0L);
        when(downPriceSnapshotCache.findLatestAtNotAfter(AS_OF))
                .thenReturn(Optional.of(metadata));
        when(downPriceSnapshotCache.findLatestAtNotAfter(snapshotAt))
                .thenReturn(Optional.of(metadata));
        when(downPriceSnapshotCache.findPage(
                metadata,
                AuctionSort.PRICE_HIGH,
                0L,
                16
        )).thenReturn(Optional.of(List.of()));

        AuctionListResponse first = auctionListService.getList(
                query(AuctionType.DOWN, AuctionSort.PRICE_HIGH, null, 1, 16, AS_OF)
        );
        AuctionListResponse second = auctionListService.getList(
                query(AuctionType.DOWN, AuctionSort.PRICE_HIGH, null, 1, 16, snapshotAt)
        );

        assertThat(first.asOf()).isEqualTo(toEpochMilli(snapshotAt));
        assertThat(second.asOf()).isEqualTo(first.asOf());
        verify(downPriceSnapshotCache).findLatestAtNotAfter(AS_OF);
        verify(downPriceSnapshotCache).findLatestAtNotAfter(snapshotAt);
    }

    @Test
    void asOf가_없으면_상한_없는_최신_Redis_세대를_조회한다() {
        LocalDateTime snapshotAt = AS_OF.minusSeconds(30);
        DownPriceSnapshotCache.Metadata metadata =
                new DownPriceSnapshotCache.Metadata(snapshotAt, 0L);
        when(downPriceSnapshotCache.findLatest()).thenReturn(Optional.of(metadata));
        when(downPriceSnapshotCache.findPage(
                metadata,
                AuctionSort.PRICE_LOW,
                0L,
                16
        )).thenReturn(Optional.of(List.of()));
        when(auctionListQueryRepository.findDownRowsByPriceSnapshots(List.of(), snapshotAt))
                .thenReturn(List.of());

        AuctionListResponse response = auctionListService.getList(
                query(AuctionType.DOWN, AuctionSort.PRICE_LOW, null, 1, 16, null)
        );

        assertThat(response.asOf()).isEqualTo(toEpochMilli(snapshotAt));
        verify(downPriceSnapshotCache).findLatest();
        verify(downPriceSnapshotCache, never()).findLatestAtNotAfter(any());
    }

    @Test
    void Redis_스냅샷이_없으면_기존_Top_K_조회로_폴백한다() {
        AuctionListSearchCondition condition = new AuctionListSearchCondition(
                AuctionType.DOWN,
                AuctionSort.PRICE_LOW,
                null,
                AS_OF
        );
        when(downPriceSnapshotCache.findLatestAtNotAfter(AS_OF))
                .thenReturn(Optional.empty());
        when(auctionListQueryRepository.count(condition)).thenReturn(1L);
        when(auctionPricePageQuery.findPage(condition, 1, 16, 1L))
                .thenReturn(List.of(downRow(1L, null, 170_000L)));

        AuctionListResponse response = auctionListService.getList(
                query(AuctionType.DOWN, AuctionSort.PRICE_LOW, null, 1, 16)
        );

        assertThat(response.asOf()).isEqualTo(toEpochMilli(AS_OF));
        verify(auctionListQueryRepository).count(condition);
        verify(auctionPricePageQuery).findPage(condition, 1, 16, 1L);
        InOrder order = inOrder(
                downPriceSnapshotCache,
                auctionRepository,
                auctionListQueryRepository,
                auctionPricePageQuery
        );
        order.verify(downPriceSnapshotCache).findLatestAtNotAfter(AS_OF);
        order.verify(auctionRepository).currentDatabaseTime();
        order.verify(auctionListQueryRepository).count(condition);
        order.verify(auctionPricePageQuery).findPage(condition, 1, 16, 1L);
        verify(auctionListQueryRepository, never())
                .findDownRowsByPriceSnapshots(any(), any());
    }

    @Test
    void 키워드가_있는_하향_가격순은_Redis를_사용하지_않는다() {
        AuctionListSearchCondition condition = new AuctionListSearchCondition(
                AuctionType.DOWN,
                AuctionSort.PRICE_HIGH,
                "상품",
                AS_OF
        );
        when(auctionListQueryRepository.count(condition)).thenReturn(0L);

        auctionListService.getList(
                query(AuctionType.DOWN, AuctionSort.PRICE_HIGH, "상품", 1, 16)
        );

        verify(downPriceSnapshotCache, never()).findLatestAtNotAfter(any());
        verify(downPriceSnapshotCache, never()).findLatest();
        verify(auctionListQueryRepository).count(condition);
    }

    @ParameterizedTest
    @EnumSource(value = AuctionSort.class, names = {"PRICE_LOW", "PRICE_HIGH"})
    void 적격_하향_가격순_hit은_요청당_lookup을_정확히_한번_기록한다(
            AuctionSort sort
    ) {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AuctionListService service = meteredService(meterRegistry);
        LocalDateTime snapshotAt = AS_OF.minusSeconds(30);
        String generation = Long.toString(toEpochMilli(snapshotAt));
        List<AuctionPriceSnapshot> snapshots = List.of(
                new AuctionPriceSnapshot(1L, 170_000L)
        );
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(zSetOperations.reverseRangeByScore(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(),
                anyLong(),
                anyLong()
        )).thenReturn(Set.of(generation));
        when(valueOperations.get(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("1");
        when(listOperations.range(
                org.mockito.ArgumentMatchers.anyString(),
                eq(0L),
                eq(0L)
        )).thenReturn(List.of("1:170000"));
        when(auctionListQueryRepository.findDownRowsByPriceSnapshots(snapshots, snapshotAt))
                .thenReturn(List.of(downRow(1L, null, 170_000L)));

        service.getList(query(AuctionType.DOWN, sort, null, 1, 16));

        assertThat(lookupTotal(meterRegistry)).isEqualTo(1D);
        assertThat(lookupCount(meterRegistry, "hit", "none")).isEqualTo(1D);
    }

    @Test
    void 적격_하향_가격순_miss도_요청당_lookup을_정확히_한번_기록한다() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AuctionListService service = meteredService(meterRegistry);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRangeByScore(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(),
                anyLong(),
                anyLong()
        )).thenReturn(Set.of());
        when(auctionListQueryRepository.count(any())).thenReturn(0L);

        service.getList(query(AuctionType.DOWN, AuctionSort.PRICE_LOW, null, 1, 16));

        assertThat(lookupTotal(meterRegistry)).isEqualTo(1D);
        assertThat(lookupCount(meterRegistry, "miss", "no_generation")).isEqualTo(1D);
    }

    @Test
    void 전체_건수가_0인_세대도_요청당_lookup_hit을_정확히_한번_기록한다() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AuctionListService service = meteredService(meterRegistry);
        LocalDateTime snapshotAt = AS_OF.minusSeconds(30);
        String generation = Long.toString(toEpochMilli(snapshotAt));
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(zSetOperations.reverseRangeByScore(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(),
                anyLong(),
                anyLong()
        )).thenReturn(Set.of(generation));
        when(valueOperations.get(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("0");
        when(auctionListQueryRepository.findDownRowsByPriceSnapshots(List.of(), snapshotAt))
                .thenReturn(List.of());

        service.getList(query(AuctionType.DOWN, AuctionSort.PRICE_LOW, null, 1, 16, null));

        assertThat(lookupTotal(meterRegistry)).isEqualTo(1D);
        assertThat(lookupCount(meterRegistry, "hit", "none")).isEqualTo(1D);
        verify(redisTemplate, never()).opsForList();
        verify(zSetOperations).reverseRangeByScore(
                org.mockito.ArgumentMatchers.anyString(),
                eq(0D),
                eq(Double.POSITIVE_INFINITY),
                eq(0L),
                eq(1L)
        );
    }

    @Test
    void 적격이_아닌_목록_요청은_lookup을_기록하지_않는다() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AuctionListService service = meteredService(meterRegistry);
        when(auctionListQueryRepository.count(any())).thenReturn(0L);
        AuctionListSearchCondition allCondition = new AuctionListSearchCondition(
                null,
                AuctionSort.PRICE_LOW,
                null,
                AS_OF
        );
        AuctionListSearchCondition upCondition = new AuctionListSearchCondition(
                AuctionType.UP,
                AuctionSort.PRICE_HIGH,
                null,
                AS_OF
        );
        AuctionListSearchCondition keywordCondition = new AuctionListSearchCondition(
                AuctionType.DOWN,
                AuctionSort.PRICE_LOW,
                "상품",
                AS_OF
        );

        service.getList(query(null, AuctionSort.PRICE_LOW, null, 1, 16));
        service.getList(query(AuctionType.UP, AuctionSort.PRICE_HIGH, null, 1, 16));
        service.getList(query(AuctionType.DOWN, AuctionSort.PRICE_LOW, "상품", 1, 16));

        assertThat(lookupTotal(meterRegistry)).isZero();
        verify(redisTemplate, never()).opsForZSet();
        InOrder order = inOrder(auctionRepository, auctionListQueryRepository);
        order.verify(auctionRepository).currentDatabaseTime();
        order.verify(auctionListQueryRepository).count(allCondition);
        order.verify(auctionRepository).currentDatabaseTime();
        order.verify(auctionListQueryRepository).count(upCondition);
        order.verify(auctionRepository).currentDatabaseTime();
        order.verify(auctionListQueryRepository).count(keywordCondition);
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
        return new AuctionListQuery(type, sort, keyword, null, List.of(), page, size, asOf);
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

    private AuctionListService meteredService(SimpleMeterRegistry meterRegistry) {
        DownPriceSnapshotCache cache = new DownPriceSnapshotCache(
                redisTemplate,
                Duration.ofMinutes(10),
                meterRegistry
        );
        return new AuctionListService(
                auctionRepository,
                auctionListQueryRepository,
                auctionPricePageQuery,
                cache,
                imageUrlResolver
        );
    }

    private double lookupTotal(SimpleMeterRegistry meterRegistry) {
        return meterRegistry.find(LOOKUP_METRIC)
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    private double lookupCount(
            SimpleMeterRegistry meterRegistry,
            String result,
            String reason
    ) {
        return meterRegistry.get(LOOKUP_METRIC)
                .tags("result", result, "reason", reason)
                .counter()
                .count();
    }
}
