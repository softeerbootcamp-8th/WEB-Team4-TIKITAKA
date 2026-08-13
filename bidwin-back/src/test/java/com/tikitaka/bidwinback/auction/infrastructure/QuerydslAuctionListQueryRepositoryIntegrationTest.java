package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.BuyNowPriceCalculator;
import com.tikitaka.bidwinback.auction.application.AuctionPricePageQuery;
import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.Bid;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.Image;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;
import com.tikitaka.bidwinback.auction.domain.enums.TradeType;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionListQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListRow;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListSearchCondition;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceCursor;
import com.tikitaka.bidwinback.auction.domain.repository.dto.DownAuctionPriceCandidate;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.storage.s3.bucket=test-bucket",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Transactional
class QuerydslAuctionListQueryRepositoryIntegrationTest {

    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 8, 3, 12, 0);
    private static final long START_PRICE = 100_000L;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AuctionListQueryRepository auctionListQueryRepository;

    @Autowired
    private AuctionPricePageQuery auctionPricePageQuery;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private BuyNowPriceCalculator buyNowPriceCalculator;

    @Test
    void Querydsl_구현체만_목록_쿼리_저장소로_등록된다() {
        Map<String, AuctionListQueryRepository> beans = applicationContext
                .getBeansOfType(AuctionListQueryRepository.class);

        assertThat(beans).hasSize(1);
        assertThat(beans.values())
                .singleElement()
                .isInstanceOf(QuerydslAuctionListQueryRepository.class);
        assertThat(beans).containsValue(auctionListQueryRepository);
    }

    @Test
    void keyword_type_asOf_조건으로_목록_건수를_계산한다() {
        Member seller = persistMember("count-seller");
        UpAuction matching = persistUp(seller, "needle 공개 경매", AuctionCategory.HOUSEHOLD);
        DownAuction wrongType = persistDown(
                seller,
                "needle 하락 경매",
                AuctionCategory.HOUSEHOLD,
                60_000L,
                10_000L,
                5L
        );
        UpAuction wrongKeyword = persistUp(seller, "다른 제목", AuctionCategory.HOUSEHOLD);
        UpAuction createdLater = persistUp(seller, "needle 이후 경매", AuctionCategory.HOUSEHOLD);
        UpAuction endedBefore = persistUp(seller, "needle 마감 경매", AuctionCategory.HOUSEHOLD);
        UpAuction completedBefore = persistUp(seller, "needle 완료 경매", AuctionCategory.HOUSEHOLD);
        UpAuction completedAfter = persistUp(seller, "needle 조회중 완료", AuctionCategory.HOUSEHOLD);

        setAuctionTimeline(matching, AS_OF.minusHours(2), AS_OF.minusDays(1));
        setAuctionTimeline(wrongType, AS_OF.minusHours(2), AS_OF.minusDays(1));
        setAuctionTimeline(wrongKeyword, AS_OF.minusHours(2), AS_OF.minusDays(1));
        setAuctionTimeline(createdLater, AS_OF.minusHours(2), AS_OF.plusMinutes(1));
        setAuctionTimeline(endedBefore, AS_OF.minusHours(2), AS_OF.minusDays(1));
        setAuctionTimeline(completedBefore, AS_OF.minusHours(2), AS_OF.minusDays(1));
        setAuctionTimeline(completedAfter, AS_OF.minusHours(2), AS_OF.minusDays(1));
        setEndedAt(endedBefore, AS_OF.minusMinutes(1));
        setCompletedAt(completedBefore, AS_OF.minusMinutes(1));
        setCompletedAt(completedAfter, AS_OF.plusMinutes(1));
        entityManager.clear();

        AuctionListSearchCondition condition = new AuctionListSearchCondition(
                AuctionType.UP,
                AuctionSort.LATEST,
                "NEEDLE",
                AS_OF
        );

        assertThat(auctionListQueryRepository.count(condition)).isEqualTo(2L);
    }

    @Test
    void keyword의_LIKE_예약문자를_문자_그대로_검색한다() {
        Member seller = persistMember("wildcard-seller");
        UpAuction plain = persistUp(seller, "일반 경매", AuctionCategory.HOUSEHOLD);
        UpAuction percent = persistUp(seller, "50% 할인 경매", AuctionCategory.HOUSEHOLD);
        UpAuction underscore = persistUp(seller, "code_name 경매", AuctionCategory.HOUSEHOLD);
        UpAuction exclamation = persistUp(seller, "느낌표! 경매", AuctionCategory.HOUSEHOLD);
        UpAuction bangPercent = persistUp(seller, "조합 !% 경매", AuctionCategory.HOUSEHOLD);
        UpAuction bangOther = persistUp(seller, "조합 !A 경매", AuctionCategory.HOUSEHOLD);
        setAuctionTimeline(plain, AS_OF.minusHours(6), AS_OF.minusHours(6));
        setAuctionTimeline(percent, AS_OF.minusHours(5), AS_OF.minusHours(5));
        setAuctionTimeline(underscore, AS_OF.minusHours(4), AS_OF.minusHours(4));
        setAuctionTimeline(exclamation, AS_OF.minusHours(3), AS_OF.minusHours(3));
        setAuctionTimeline(bangPercent, AS_OF.minusHours(2), AS_OF.minusHours(2));
        setAuctionTimeline(bangOther, AS_OF.minusHours(1), AS_OF.minusHours(1));
        entityManager.clear();

        assertThat(countByKeyword("%")).isEqualTo(2L);
        assertThat(countByKeyword("_")).isEqualTo(1L);
        assertThat(countByKeyword("!")).isEqualTo(3L);
        assertThat(countByKeyword("!%")).isEqualTo(1L);
    }

    @Test
    void title_검색은_영문_대소문자를_구분하지_않는다() {
        Member seller = persistMember("case-insensitive-seller");
        UpAuction mixedCase = persistUp(seller, "iPhone 케이스", AuctionCategory.HOUSEHOLD);
        UpAuction upperCase = persistUp(seller, "IPHONE 충전기", AuctionCategory.HOUSEHOLD);
        setAuctionTimeline(mixedCase, AS_OF.minusHours(2), AS_OF.minusHours(2));
        setAuctionTimeline(upperCase, AS_OF.minusHours(1), AS_OF.minusHours(1));
        entityManager.clear();

        long lowerCaseCount = countByKeyword("iphone");
        long upperCaseCount = countByKeyword("IPHONE");

        assertThat(lowerCaseCount).isEqualTo(2L);
        assertThat(upperCaseCount).isEqualTo(lowerCaseCount);
    }

    @Test
    void page의_limit과_offset을_적용한다() {
        Member seller = persistMember("page-seller");
        UpAuction oldest = persistUp(seller, "가장 오래된 경매", AuctionCategory.HOUSEHOLD);
        UpAuction middle = persistUp(seller, "중간 경매", AuctionCategory.HOUSEHOLD);
        UpAuction newest = persistUp(seller, "가장 최신 경매", AuctionCategory.HOUSEHOLD);

        setAuctionTimeline(oldest, AS_OF.minusHours(3), AS_OF.minusHours(3));
        setAuctionTimeline(middle, AS_OF.minusHours(2), AS_OF.minusHours(2));
        setAuctionTimeline(newest, AS_OF.minusHours(1), AS_OF.minusHours(1));
        entityManager.clear();

        AuctionListSearchCondition condition = condition(AuctionSort.LATEST);
        List<AuctionListRow> page = findPage(condition, 1, 2);

        assertThat(page)
                .extracting(AuctionListRow::auctionId)
                .containsExactly(middle.getId(), oldest.getId());
    }

    @Test
    void 모든_정렬은_동률이어도_auction_id로_순서를_고정한다() {
        Member seller = persistMember("sort-seller");
        UpAuction first = persistUp(seller, "동률 경매 A", AuctionCategory.HOUSEHOLD);
        UpAuction second = persistUp(seller, "동률 경매 B", AuctionCategory.HOUSEHOLD);
        LocalDateTime tiedAt = AS_OF.minusHours(1);
        setAuctionTimeline(first, tiedAt, tiedAt);
        setAuctionTimeline(second, tiedAt, tiedAt);
        entityManager.clear();

        for (AuctionSort sort : AuctionSort.values()) {
            List<AuctionListRow> rows = findPage(condition(sort), 0, 2);
            List<Long> expectedIds = sort == AuctionSort.DEADLINE
                    ? List.of(first.getId(), second.getId())
                    : List.of(second.getId(), first.getId());

            assertThat(rows)
                    .as("sort=%s", sort)
                    .extracting(AuctionListRow::auctionId)
                    .containsExactlyElementsOf(expectedIds);
        }
    }

    @Test
    void 낮은가격_경계_나머지는_같은_최저가의_뒤쪽_후보만_조회한다() {
        // given
        Member seller = persistMember("minimum-price-bound-seller");
        DownAuction first = persistDown(
                seller,
                "최저가 동률 A",
                AuctionCategory.HOUSEHOLD,
                60_000L,
                10_000L,
                5L
        );
        DownAuction second = persistDown(
                seller,
                "최저가 동률 B",
                AuctionCategory.HOUSEHOLD,
                60_000L,
                10_000L,
                5L
        );
        DownAuction third = persistDown(
                seller,
                "최저가 동률 C",
                AuctionCategory.HOUSEHOLD,
                60_000L,
                10_000L,
                5L
        );
        DownAuction differentBound = persistDown(
                seller,
                "다른 최저가",
                AuctionCategory.HOUSEHOLD,
                50_000L,
                10_000L,
                5L
        );
        setAuctionTimeline(first, AS_OF.minusHours(3), AS_OF.minusHours(3));
        setAuctionTimeline(second, AS_OF.minusHours(2), AS_OF.minusHours(2));
        setAuctionTimeline(third, AS_OF.minusHours(1), AS_OF.minusHours(1));
        setAuctionTimeline(differentBound, AS_OF.minusHours(1), AS_OF.minusHours(1));
        setCompletedAt(second, AS_OF.plusMinutes(1));
        entityManager.clear();

        AuctionListSearchCondition condition = new AuctionListSearchCondition(
                AuctionType.DOWN,
                AuctionSort.PRICE_LOW,
                null,
                AS_OF
        );

        // when
        List<DownAuctionPriceCandidate> remaining = auctionListQueryRepository
                .findRemainingDownPriceCandidatesAtBound(
                        condition,
                        new AuctionPriceCursor(60_000L, third.getId())
                );

        // then
        assertThat(remaining)
                .extracting(DownAuctionPriceCandidate::auctionId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void 높은가격_경계_나머지는_같은_시작가의_뒤쪽_후보만_조회한다() {
        // given
        Member seller = persistMember("start-price-bound-seller");
        DownAuction first = persistDown(
                seller,
                "시작가 동률 A",
                AuctionCategory.HOUSEHOLD,
                60_000L,
                10_000L,
                5L
        );
        DownAuction second = persistDown(
                seller,
                "시작가 동률 B",
                AuctionCategory.HOUSEHOLD,
                60_000L,
                10_000L,
                5L
        );
        DownAuction third = persistDown(
                seller,
                "시작가 동률 C",
                AuctionCategory.HOUSEHOLD,
                60_000L,
                10_000L,
                5L
        );
        DownAuction differentBound = persistDown(
                seller,
                "다른 시작가",
                AuctionCategory.HOUSEHOLD,
                60_000L,
                10_000L,
                5L
        );
        setAuctionTimeline(first, AS_OF.minusHours(3), AS_OF.minusHours(3));
        setAuctionTimeline(second, AS_OF.minusHours(2), AS_OF.minusHours(2));
        setAuctionTimeline(third, AS_OF.minusHours(1), AS_OF.minusHours(1));
        setAuctionTimeline(differentBound, AS_OF.minusHours(1), AS_OF.minusHours(1));
        setStartPrice(differentBound, 90_000L);
        entityManager.clear();

        AuctionListSearchCondition condition = new AuctionListSearchCondition(
                AuctionType.DOWN,
                AuctionSort.PRICE_HIGH,
                null,
                AS_OF
        );

        // when
        List<DownAuctionPriceCandidate> remaining = auctionListQueryRepository
                .findRemainingDownPriceCandidatesAtBound(
                        condition,
                        new AuctionPriceCursor(START_PRICE, third.getId())
                );

        // then
        assertThat(remaining)
                .extracting(DownAuctionPriceCandidate::auctionId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void 높은가격_스냅샷_분기는_완료시점_기준으로_합쳐_정렬한다() {
        Member seller = persistMember("start-price-snapshot-seller");
        DownAuction completedBeforeAsOf = persistDown(
                seller,
                "스냅샷 이전 완료 경매",
                AuctionCategory.HOUSEHOLD,
                60_000L,
                10_000L,
                5L
        );
        DownAuction notCompleted = persistDown(
                seller,
                "미완료 경매",
                AuctionCategory.HOUSEHOLD,
                60_000L,
                10_000L,
                5L
        );
        DownAuction completedAfterAsOf = persistDown(
                seller,
                "스냅샷 이후 완료 경매",
                AuctionCategory.HOUSEHOLD,
                60_000L,
                10_000L,
                5L
        );
        DownAuction lowerNotCompleted = persistDown(
                seller,
                "낮은 미완료 경매",
                AuctionCategory.HOUSEHOLD,
                60_000L,
                10_000L,
                5L
        );

        for (DownAuction auction : List.of(
                completedBeforeAsOf,
                notCompleted,
                completedAfterAsOf,
                lowerNotCompleted
        )) {
            setAuctionTimeline(auction, AS_OF.minusHours(1), AS_OF.minusHours(1));
        }
        setStartPrice(completedBeforeAsOf, 500_000L);
        setStartPrice(notCompleted, 400_000L);
        setStartPrice(completedAfterAsOf, 400_000L);
        setStartPrice(lowerNotCompleted, 300_000L);
        setCompletedAt(completedBeforeAsOf, AS_OF.minusMinutes(1));
        setCompletedAt(completedAfterAsOf, AS_OF.plusMinutes(1));
        entityManager.clear();

        AuctionListSearchCondition condition = new AuctionListSearchCondition(
                AuctionType.DOWN,
                AuctionSort.PRICE_HIGH,
                null,
                AS_OF
        );

        List<DownAuctionPriceCandidate> candidates = auctionListQueryRepository
                .findDownPriceCandidates(condition, null, 3);

        assertThat(candidates)
                .extracting(DownAuctionPriceCandidate::auctionId)
                .containsExactly(
                        completedAfterAsOf.getId(),
                        notCompleted.getId(),
                        lowerNotCompleted.getId()
                )
                .doesNotContain(completedBeforeAsOf.getId());
    }

    @Test
    void 추천순_UP_경매는_asOf와_무관하게_현재가와_전체_입찰수를_사용한다() {
        Member seller = persistMember("up-seller");
        Member bidder = persistMember("up-bidder");
        UpAuction auction = persistUp(seller, "입찰 집계 경매", AuctionCategory.HOUSEHOLD);
        setAuctionTimeline(auction, AS_OF.minusHours(1), AS_OF.minusHours(1));

        Bid first = persistBid(auction, bidder, 120_000L);
        Bid second = persistBid(auction, bidder, 180_000L);
        Bid atAsOf = persistBid(auction, bidder, 200_000L);
        Bid afterAsOf = persistBid(auction, bidder, 999_000L);
        setBidCreatedAt(first, AS_OF.minusMinutes(2));
        setBidCreatedAt(second, AS_OF.minusMinutes(1));
        setBidCreatedAt(atAsOf, AS_OF);
        setBidCreatedAt(afterAsOf, AS_OF.plusSeconds(1));
        entityManager.clear();

        List<AuctionListRow> rows = findPage(
                condition(AuctionSort.RECOMMENDED),
                0,
                10
        );

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.auctionId()).isEqualTo(auction.getId());
            assertThat(row.currentPrice()).isEqualTo(999_000L);
            assertThat(row.bidCount()).isEqualTo(4L);
        });
    }

    @Test
    void 추천순은_asOf_이후에_완료됐어도_현재_완료된_경매를_제외한다() {
        Member seller = persistMember("recommended-current-seller");
        UpAuction ongoing = persistUp(seller, "진행 중 경매", AuctionCategory.HOUSEHOLD);
        UpAuction completed = persistUp(seller, "완료된 경매", AuctionCategory.HOUSEHOLD);
        setAuctionTimeline(ongoing, AS_OF.minusHours(2), AS_OF.minusHours(2));
        setAuctionTimeline(completed, AS_OF.minusHours(1), AS_OF.minusHours(1));
        setCompletedAt(completed, AS_OF.plusMinutes(1));
        entityManager.clear();

        AuctionListSearchCondition condition = new AuctionListSearchCondition(
                AuctionType.UP,
                AuctionSort.RECOMMENDED,
                null,
                AS_OF
        );

        assertThat(auctionListQueryRepository.count(condition)).isEqualTo(1L);
        assertThat(findPage(condition, 0, 10))
                .extracting(AuctionListRow::auctionId)
                .containsExactly(ongoing.getId());
    }

    @Test
    void 추천순과_가격순은_입찰_집계와_경매_유형별_현재가로_정렬한다() {
        Member seller = persistMember("aggregate-sort-seller");
        Member bidder = persistMember("aggregate-sort-bidder");
        UpAuction noBid = persistUp(seller, "입찰 없는 상향 경매", AuctionCategory.HOUSEHOLD);
        UpAuction highest = persistUp(seller, "최고가 상향 경매", AuctionCategory.HOUSEHOLD);
        DownAuction lowest = persistDown(
                seller,
                "최저가 하향 경매",
                AuctionCategory.HOUSEHOLD,
                60_000L,
                10_000L,
                5L
        );
        setAuctionTimeline(noBid, AS_OF.minusHours(3), AS_OF.minusHours(1));
        setAuctionTimeline(highest, AS_OF.minusHours(2), AS_OF.minusHours(1));
        setAuctionTimeline(lowest, AS_OF.minusHours(1), AS_OF.minusMinutes(20));
        Bid bid = persistBid(highest, bidder, 180_000L);
        setBidCreatedAt(bid, AS_OF.minusMinutes(1));
        entityManager.clear();

        assertThat(findPage(
                condition(AuctionSort.RECOMMENDED),
                0,
                3
        )).extracting(AuctionListRow::auctionId)
                .containsExactly(highest.getId(), lowest.getId(), noBid.getId());
        assertThat(findPage(
                condition(AuctionSort.PRICE_LOW),
                0,
                3
        )).extracting(AuctionListRow::auctionId)
                .containsExactly(lowest.getId(), noBid.getId(), highest.getId());
        assertThat(findPage(
                condition(AuctionSort.PRICE_HIGH),
                0,
                3
        )).extracting(AuctionListRow::auctionId)
                .containsExactly(highest.getId(), noBid.getId(), lowest.getId());
    }

    @Test
    void DOWN_경매는_분_경계에서_가격을_내리고_최저가_아래로_내리지_않는다() {
        Member seller = persistMember("down-seller");
        DownAuction auction = persistDown(
                seller,
                "하락 경매",
                AuctionCategory.HOUSEHOLD,
                60_000L,
                10_000L,
                5L
        );
        setAuctionTimeline(auction, AS_OF.minusHours(1), AS_OF);
        entityManager.clear();

        assertThat(currentPriceAt(auction, AS_OF.plusMinutes(4))).isEqualTo(100_000L);
        assertThat(currentPriceAt(auction, AS_OF.plusMinutes(5))).isEqualTo(90_000L);
        assertThat(currentPriceAt(auction, AS_OF.plusMinutes(20))).isEqualTo(60_000L);
        assertThat(currentPriceAt(auction, AS_OF.plusMinutes(21))).isEqualTo(60_000L);
    }

    @Test
    void DOWN_목록_현재가는_즉시구매_가격_계산과_일치한다() {
        Member seller = persistMember("down-price-consistency-seller");
        DownAuction auction = persistDown(
                seller,
                "하락가 일치 경매",
                AuctionCategory.HOUSEHOLD,
                60_000L,
                30_000L,
                5L
        );

        for (long elapsedMinutes : List.of(0L, 4L, 5L, 6L, 9L, 10L, 11L, 15L, 1000L)) {
            LocalDateTime startedAt = AS_OF.minusMinutes(elapsedMinutes);
            setAuctionTimeline(auction, startedAt, startedAt);
            entityManager.clear();

            long listCurrentPrice = currentPriceAt(auction, AS_OF);
            DownAuction persistedAuction = entityManager.find(DownAuction.class, auction.getId());
            long buyNowPrice = buyNowPriceCalculator.calculate(persistedAuction, AS_OF);

            assertThat(listCurrentPrice)
                    .as("elapsedMinutes=%s", elapsedMinutes)
                    .isEqualTo(buyNowPrice);
        }
    }

    @Test
    void 최소_image_id의_object_key와_목록_projection을_조회한다() {
        Member seller = persistMember("image-seller");
        UpAuction auction = persistUp(seller, "대표 이미지 경매", AuctionCategory.HOUSEHOLD);
        LocalDateTime listedAt = AS_OF.minusHours(1);
        LocalDateTime startedAt = AS_OF.minusHours(1);
        setAuctionTimeline(auction, listedAt, startedAt);
        Image first = persistImage(auction, "images/first-" + UUID.randomUUID());
        persistImage(auction, "images/second-" + UUID.randomUUID());
        persistImage(auction, "images/third-" + UUID.randomUUID());
        entityManager.flush();
        entityManager.clear();

        List<AuctionListRow> rows = findPage(
                condition(AuctionSort.LATEST),
                0,
                10
        );

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.auctionId()).isEqualTo(auction.getId());
            assertThat(row.auctionType()).isEqualTo(AuctionType.UP);
            assertThat(row.title()).isEqualTo("대표 이미지 경매");
            assertThat(row.sellerName()).isEqualTo(seller.getNickname());
            assertThat(row.category()).isEqualTo(AuctionCategory.HOUSEHOLD);
            assertThat(row.thumbnailObjectKey()).isEqualTo(first.getObjectKey());
            assertThat(row.currentPrice()).isEqualTo(START_PRICE);
            assertThat(row.startPrice()).isEqualTo(START_PRICE);
            assertThat(row.bidCount()).isZero();
            assertThat(row.deadline()).isEqualTo(auction.getEndedAt());
            assertThat(row.listedAt()).isEqualTo(listedAt);
            assertThat(row.status()).isEqualTo(AuctionStatus.OPEN);
            assertThat(row.revision()).isZero();
            assertThat(row.minimumPrice()).isNull();
            assertThat(row.dropPrice()).isNull();
            assertThat(row.priceDropInterval()).isNull();
            assertThat(row.startedAt()).isEqualTo(startedAt);
        });
    }

    @Test
    void DOWN_경매의_목록_projection을_조회한다() {
        Member seller = persistMember("down-projection-seller");
        DownAuction auction = persistDown(
                seller,
                "하락 경매 projection",
                AuctionCategory.FOOD,
                60_000L,
                10_000L,
                5L
        );
        LocalDateTime listedAt = AS_OF.minusHours(2);
        LocalDateTime startedAt = AS_OF.minusMinutes(10);
        setAuctionTimeline(auction, listedAt, startedAt);
        entityManager.clear();

        List<AuctionListRow> rows = findPage(
                condition(AuctionSort.LATEST),
                0,
                10
        );

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.auctionId()).isEqualTo(auction.getId());
            assertThat(row.auctionType()).isEqualTo(AuctionType.DOWN);
            assertThat(row.title()).isEqualTo("하락 경매 projection");
            assertThat(row.sellerName()).isEqualTo(seller.getNickname());
            assertThat(row.category()).isEqualTo(AuctionCategory.FOOD);
            assertThat(row.thumbnailObjectKey()).isNull();
            assertThat(row.currentPrice()).isEqualTo(80_000L);
            assertThat(row.startPrice()).isEqualTo(START_PRICE);
            assertThat(row.bidCount()).isZero();
            assertThat(row.deadline()).isEqualTo(auction.getEndedAt());
            assertThat(row.listedAt()).isEqualTo(listedAt);
            assertThat(row.status()).isEqualTo(AuctionStatus.OPEN);
            assertThat(row.revision()).isZero();
            assertThat(row.minimumPrice()).isEqualTo(60_000L);
            assertThat(row.dropPrice()).isEqualTo(10_000L);
            assertThat(row.priceDropInterval()).isEqualTo(5L);
            assertThat(row.startedAt()).isEqualTo(startedAt);
        });
    }

    @Test
    void 목록_조회는_엔티티를_영속성_컨텍스트에_적재하지_않는다() {
        Member seller = persistMember("scalar-projection-seller");
        UpAuction auction = persistUp(
                seller,
                "scalar projection 경매",
                AuctionCategory.HOUSEHOLD
        );
        setAuctionTimeline(auction, AS_OF.minusHours(1), AS_OF.minusHours(1));
        entityManager.clear();
        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        List<AuctionListRow> rows = findPage(
                condition(AuctionSort.LATEST),
                0,
                10
        );

        assertThat(rows).extracting(AuctionListRow::auctionId)
                .containsExactly(auction.getId());
        assertThat(statistics.getEntityLoadCount()).isZero();
    }

    private AuctionListSearchCondition condition(AuctionSort sort) {
        return new AuctionListSearchCondition(null, sort, null, AS_OF);
    }

    private List<AuctionListRow> findPage(
            AuctionListSearchCondition condition,
            long offset,
            int limit
    ) {
        if (condition.sort() != AuctionSort.PRICE_LOW
                && condition.sort() != AuctionSort.PRICE_HIGH) {
            return auctionListQueryRepository.findPage(condition, offset, limit);
        }
        if (offset % limit != 0) {
            throw new IllegalArgumentException("가격순 테스트 페이지의 offset이 limit 배수가 아닙니다.");
        }
        int page = Math.toIntExact(offset / limit) + 1;
        return auctionPricePageQuery.findPage(
                condition,
                page,
                limit,
                auctionListQueryRepository.count(condition)
        );
    }

    private long countByKeyword(String keyword) {
        return auctionListQueryRepository.count(new AuctionListSearchCondition(
                null,
                AuctionSort.LATEST,
                keyword,
                AS_OF
        ));
    }

    private long currentPriceAt(DownAuction auction, LocalDateTime asOf) {
        List<AuctionListRow> rows = findPage(
                new AuctionListSearchCondition(AuctionType.DOWN, AuctionSort.PRICE_LOW, null, asOf),
                0,
                1
        );
        assertThat(rows).singleElement()
                .extracting(AuctionListRow::auctionId)
                .isEqualTo(auction.getId());
        return rows.getFirst().currentPrice();
    }

    private UpAuction persistUp(Member seller, String title, AuctionCategory category) {
        UpAuction auction = UpAuction.builder()
                .seller(seller)
                .title(title)
                .description("경매 목록 조회 통합 테스트")
                .status(AuctionStatus.OPEN)
                .category(category)
                .startPrice(START_PRICE)
                .endedAt(AS_OF.plusDays(1))
                .tradeType(TradeType.DELIVERY)
                .contact("01012345678")
                .buyNowPrice(300_000L)
                .build();
        entityManager.persist(auction);
        entityManager.flush();
        return auction;
    }

    private DownAuction persistDown(
            Member seller,
            String title,
            AuctionCategory category,
            long minimumPrice,
            long dropPrice,
            long priceDropInterval
    ) {
        DownAuction auction = DownAuction.builder()
                .seller(seller)
                .title(title)
                .description("경매 목록 조회 통합 테스트")
                .status(AuctionStatus.OPEN)
                .category(category)
                .startPrice(START_PRICE)
                .endedAt(AS_OF.plusDays(1))
                .tradeType(TradeType.DELIVERY)
                .contact("01012345678")
                .minimumPrice(minimumPrice)
                .dropPrice(dropPrice)
                .priceDropInterval(priceDropInterval)
                .build();
        entityManager.persist(auction);
        entityManager.flush();
        return auction;
    }

    private Bid persistBid(Auction auction, Member bidder, long price) {
        Bid bid = Bid.builder()
                .auction(auction)
                .bidder(bidder)
                .price(price)
                .status(BidStatus.UP)
                .build();
        entityManager.persist(bid);
        entityManager.flush();
        entityManager.createNativeQuery("""
                        UPDATE auction
                        SET bid_count = bid_count + 1
                        WHERE id = :auctionId
                        """)
                .setParameter("auctionId", auction.getId())
                .executeUpdate();
        if (auction instanceof UpAuction) {
            // 운영 입찰 경로와 동일하게 상향 경매 행의 current_price도 함께 전진시킨다.
            entityManager.createNativeQuery("""
                            UPDATE auction
                            SET current_price = GREATEST(current_price, :price)
                            WHERE id = :auctionId
                            """)
                    .setParameter("price", price)
                    .setParameter("auctionId", auction.getId())
                    .executeUpdate();
        }
        return bid;
    }

    private Image persistImage(Auction auction, String objectKey) {
        Image image = Image.builder()
                .auction(auction)
                .objectKey(objectKey)
                .build();
        entityManager.persist(image);
        return image;
    }

    private Member persistMember(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Member member = Member.builder()
                .email(prefix + "-" + suffix + "@example.com")
                .password("encoded-password")
                .name("통합테스트")
                .phoneNumber("01012345678")
                .nickname("n" + suffix)
                .status(MemberStatus.ACTIVE)
                .build();
        entityManager.persist(member);
        return member;
    }

    private void setAuctionTimeline(
            Auction auction,
            LocalDateTime createdAt,
            LocalDateTime startedAt
    ) {
        entityManager.flush();
        entityManager.createNativeQuery("""
                        UPDATE auction
                        SET created_at = :createdAt,
                            started_at = :startedAt
                        WHERE id = :auctionId
                        """)
                .setParameter("createdAt", createdAt)
                .setParameter("startedAt", startedAt)
                .setParameter("auctionId", auction.getId())
                .executeUpdate();
    }

    private void setBidCreatedAt(Bid bid, LocalDateTime createdAt) {
        entityManager.flush();
        entityManager.createNativeQuery("""
                        UPDATE bid
                        SET created_at = :createdAt
                        WHERE id = :bidId
                        """)
                .setParameter("createdAt", createdAt)
                .setParameter("bidId", bid.getId())
                .executeUpdate();
    }

    private void setEndedAt(Auction auction, LocalDateTime endedAt) {
        entityManager.createNativeQuery("""
                        UPDATE auction
                        SET ended_at = :endedAt
                        WHERE id = :auctionId
                        """)
                .setParameter("endedAt", endedAt)
                .setParameter("auctionId", auction.getId())
                .executeUpdate();
    }

    private void setStartPrice(Auction auction, long startPrice) {
        entityManager.createNativeQuery("""
                        UPDATE auction
                        SET start_price = :startPrice
                        WHERE id = :auctionId
                        """)
                .setParameter("startPrice", startPrice)
                .setParameter("auctionId", auction.getId())
                .executeUpdate();
    }

    private void setCompletedAt(Auction auction, LocalDateTime completedAt) {
        entityManager.createNativeQuery("""
                        UPDATE auction
                        SET completed_at = :completedAt
                        WHERE id = :auctionId
                        """)
                .setParameter("completedAt", completedAt)
                .setParameter("auctionId", auction.getId())
                .executeUpdate();
    }
}
