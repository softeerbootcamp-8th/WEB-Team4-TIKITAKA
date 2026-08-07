package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.Image;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.BidRepository;
import com.tikitaka.bidwinback.auction.domain.repository.ImageRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionBidSummary;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionListResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionSummaryResponse;
import com.tikitaka.bidwinback.global.storage.ImageUrlResolver;
import com.tikitaka.bidwinback.member.domain.entity.Member;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionListServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 8, 1, 12, 0);
    private static final LocalDateTime SERVER_TIME = AS_OF.plusMinutes(1);

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private ImageUrlResolver imageUrlResolver;

    @Mock
    private BuyNowPriceCalculator buyNowPriceCalculator;

    private AuctionListService auctionListService;

    @BeforeEach
    void setUp() {
        auctionListService = new AuctionListService(
                auctionRepository,
                bidRepository,
                imageRepository,
                imageUrlResolver,
                buyNowPriceCalculator
        );
        lenient().when(imageRepository.findFirstImageByAuctionIds(anyList())).thenReturn(List.of());
        lenient().when(auctionRepository.currentDatabaseTime()).thenReturn(SERVER_TIME);
    }

    @Test
    void auctionType으로_필터링하면_해당_타입만_남는다() {
        UpAuction upAuction = upAuction(1L, 200_000L);
        // 필터에 걸려 제외될 대상이라 getter가 하나도 안 불릴 수 있다 — instanceof 판별에만 필요하다.
        DownAuction downAuction = mock(DownAuction.class);
        when(auctionRepository.findAllForList(null, AS_OF)).thenReturn(List.of(upAuction, downAuction));
        when(bidRepository.summarizeByAuctionIds(anyList(), any())).thenReturn(List.of());

        AuctionListResponse response = auctionListService.getList(query(AuctionType.UP, AuctionSort.LATEST, null, 1, 16));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).auctionId()).isEqualTo(1L);
        assertThat(response.totalCount()).isEqualTo(1);
    }

    @Test
    void 상향_경매는_최고_입찰가가_없으면_시작가를_현재가로_쓴다() {
        UpAuction auction = upAuction(1L, 200_000L);
        when(auctionRepository.findAllForList(null, AS_OF)).thenReturn(List.of(auction));
        when(bidRepository.summarizeByAuctionIds(anyList(), any())).thenReturn(List.of());

        AuctionListResponse response = auctionListService.getList(query(null, AuctionSort.LATEST, null, 1, 16));

        AuctionSummaryResponse summary = response.items().get(0);
        assertThat(summary.currentPrice()).isEqualTo(200_000L);
        assertThat(summary.bidCount()).isZero();
    }

    @Test
    void 상향_경매는_asOf_이전_최고_입찰가를_현재가로_쓴다() {
        UpAuction auction = upAuction(1L, 200_000L);
        when(auctionRepository.findAllForList(null, AS_OF)).thenReturn(List.of(auction));
        when(bidRepository.summarizeByAuctionIds(List.of(1L), AS_OF))
                .thenReturn(List.of(new AuctionBidSummary(1L, 260_000L, 4L)));

        AuctionListResponse response = auctionListService.getList(
                query(null, AuctionSort.LATEST, null, 1, 16, AS_OF)
        );

        AuctionSummaryResponse summary = response.items().get(0);
        assertThat(summary.currentPrice()).isEqualTo(260_000L);
        assertThat(summary.bidCount()).isEqualTo(4L);
    }

    @Test
    void 하향_경매_현재가는_BuyNowPriceCalculator가_계산한_값을_그대로_쓴다() {
        // 하락 주기 공식 자체(경과 횟수 계산, 최저가 하한)는 BuyNowPriceCalculatorTest가 검증한다.
        // 여기서는 목록 조회가 그 계산기에 startedAt 기준 asOf를 그대로 넘기고,
        // 반환값을 currentPrice로 그대로 쓰는지만 확인한다.
        DownAuction auction = mock(DownAuction.class);
        stubCommon(auction, 1L, 200_000L);
        when(auction.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 8, 1, 9, 0));
        when(auction.getStartedAt()).thenReturn(LocalDateTime.of(2026, 8, 1, 9, 0));
        when(auction.getMinimumPrice()).thenReturn(150_000L);
        when(auction.getDropPrice()).thenReturn(10_000L);
        when(auction.getPriceDropInterval()).thenReturn(10L);
        LocalDateTime asOf = LocalDateTime.of(2026, 8, 1, 12, 35);
        when(auctionRepository.findAllForList(null, asOf)).thenReturn(List.of(auction));
        when(bidRepository.summarizeByAuctionIds(anyList(), any())).thenReturn(List.of());
        when(buyNowPriceCalculator.calculate(auction, asOf)).thenReturn(170_000L);

        AuctionListResponse response = auctionListService.getList(
                query(null, AuctionSort.LATEST, null, 1, 16, asOf)
        );

        AuctionSummaryResponse summary = response.items().get(0);
        assertThat(summary.currentPrice()).isEqualTo(170_000L);
        assertThat(summary.downPricing().minimumPrice()).isEqualTo(150_000L);
        assertThat(summary.downPricing().dropPrice()).isEqualTo(10_000L);
        assertThat(summary.downPricing().priceDropIntervalMs()).isEqualTo(600_000L);
        assertThat(summary.downPricing().startedAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 1, 9, 0)
                        .atZone(SEOUL)
                        .toInstant()
                        .toEpochMilli());
    }

    @Test
    void 추천순은_입찰수_내림차순이다() {
        UpAuction popular = upAuction(1L, 200_000L);
        UpAuction quiet = upAuction(2L, 200_000L);
        when(auctionRepository.findAllForList(null, AS_OF)).thenReturn(List.of(quiet, popular));
        when(bidRepository.summarizeByAuctionIds(anyList(), any())).thenReturn(List.of(
                new AuctionBidSummary(1L, 300_000L, 10L),
                new AuctionBidSummary(2L, 210_000L, 1L)
        ));

        AuctionListResponse response = auctionListService.getList(
                query(null, AuctionSort.RECOMMENDED, null, 1, 16)
        );

        assertThat(response.items()).extracting(AuctionSummaryResponse::auctionId)
                .containsExactly(1L, 2L);
    }

    @Test
    void 낮은_가격순으로_정렬한다() {
        UpAuction cheap = upAuction(1L, 100_000L);
        UpAuction expensive = upAuction(2L, 500_000L);
        when(auctionRepository.findAllForList(null, AS_OF)).thenReturn(List.of(expensive, cheap));
        when(bidRepository.summarizeByAuctionIds(anyList(), any())).thenReturn(List.of());

        AuctionListResponse response = auctionListService.getList(
                query(null, AuctionSort.PRICE_LOW, null, 1, 16)
        );

        assertThat(response.items()).extracting(AuctionSummaryResponse::auctionId)
                .containsExactly(1L, 2L);
    }

    @Test
    void 페이지_크기만큼_잘라서_돌려주고_전체_개수와_페이지수를_함께_준다() {
        List<Auction> auctions = List.of(
                upAuction(1L, 100_000L),
                upAuction(2L, 100_000L),
                upAuction(3L, 100_000L)
        );
        when(auctionRepository.findAllForList(null, AS_OF)).thenReturn(auctions);
        when(bidRepository.summarizeByAuctionIds(anyList(), any())).thenReturn(List.of());

        AuctionListResponse response = auctionListService.getList(
                query(null, AuctionSort.LATEST, null, 1, 2)
        );

        assertThat(response.items()).hasSize(2);
        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.page()).isEqualTo(1);
    }

    @Test
    void 서버_시각과_목록_스냅샷_시각을_각각_응답한다() {
        when(auctionRepository.findAllForList(null, AS_OF)).thenReturn(List.of());
        when(auctionRepository.currentDatabaseTime()).thenReturn(AS_OF);

        AuctionListResponse response = auctionListService.getList(
                query(null, AuctionSort.LATEST, null, 1, 16, null)
        );

        assertThat(response.serverTime()).isEqualTo(AS_OF.atZone(SEOUL).toInstant().toEpochMilli());
        assertThat(response.asOf()).isEqualTo(AS_OF.atZone(SEOUL).toInstant().toEpochMilli());
    }

    @Test
    void 다음_페이지는_asOf를_유지하면서_최신_서버_시각을_응답한다() {
        when(auctionRepository.findAllForList(null, AS_OF)).thenReturn(List.of());

        AuctionListResponse response = auctionListService.getList(
                query(null, AuctionSort.LATEST, null, 2, 16, AS_OF)
        );

        assertThat(response.serverTime())
                .isEqualTo(SERVER_TIME.atZone(SEOUL).toInstant().toEpochMilli());
        assertThat(response.asOf()).isEqualTo(AS_OF.atZone(SEOUL).toInstant().toEpochMilli());
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

    private UpAuction upAuction(long id, long startPrice) {
        UpAuction auction = mock(UpAuction.class);
        stubCommon(auction, id, startPrice);
        when(auction.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 8, 1, 9, 0));
        return auction;
    }

    private void stubCommon(Auction auction, long id, long startPrice) {
        Member seller = mock(Member.class);
        when(seller.getNickname()).thenReturn("판매자" + id);

        when(auction.getId()).thenReturn(id);
        when(auction.getTitle()).thenReturn("상품" + id);
        when(auction.getCategory()).thenReturn(AuctionCategory.HOUSEHOLD);
        when(auction.getStartPrice()).thenReturn(startPrice);
        when(auction.getEndedAt()).thenReturn(LocalDateTime.of(2026, 8, 1, 18, 0));
        when(auction.getStatus()).thenReturn(AuctionStatus.BID_ONGOING);
        when(auction.getRevision()).thenReturn(id);
        when(auction.getSeller()).thenReturn(seller);
    }
}
