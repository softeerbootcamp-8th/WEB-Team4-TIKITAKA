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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    private ImageUrlResolver imageUrlResolver;

    private AuctionListService auctionListService;

    @BeforeEach
    void setUp() {
        auctionListService = new AuctionListService(
                auctionRepository,
                auctionListQueryRepository,
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
        return new AuctionListQuery(type, sort, keyword, page, size, asOf);
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
