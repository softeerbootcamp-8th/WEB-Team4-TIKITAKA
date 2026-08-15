package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.TradeType;
import com.tikitaka.bidwinback.auction.domain.repository.DownPriceSnapshotQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.DownPriceSnapshotRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.storage.s3.bucket=test-bucket")
@Transactional
class QuerydslDownPriceSnapshotQueryRepositoryIntegrationTest {

    private static final LocalDateTime SNAPSHOT_AT = LocalDateTime.of(2026, 8, 3, 12, 0);
    private static final long START_PRICE = 100_000L;

    @Autowired
    private DownPriceSnapshotQueryRepository snapshotQueryRepository;

    @Autowired
    private DownPriceSnapshotRepository snapshotRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 요청시각_이하의_가장_최신_스냅샷_세대를_조회한다() {
        Member seller = persistMember("latest-snapshot-seller");
        DownAuction auction = persistDown(seller, "최신 세대", 60_000L);
        LocalDateTime olderSnapshotAt = SNAPSHOT_AT.minusMinutes(10);
        setAuctionStartedAt(auction, olderSnapshotAt.minusMinutes(5));
        entityManager.flush();
        assertThat(snapshotRepository.capture(olderSnapshotAt)).isEqualTo(1);
        assertThat(snapshotRepository.capture(SNAPSHOT_AT)).isEqualTo(1);

        assertThat(snapshotQueryRepository.findLatestSnapshotAtNotAfter(
                olderSnapshotAt.plusMinutes(5)
        )).contains(olderSnapshotAt);
        assertThat(snapshotQueryRepository.findLatestSnapshotAtNotAfter(
                SNAPSHOT_AT.plusMinutes(1)
        )).contains(SNAPSHOT_AT);
    }

    @Test
    void 가격순은_동률에서_auction_id_내림차순을_적용한다() {
        Member seller = persistMember("price-order-seller");
        DownAuction tiedFirst = persistDown(seller, "동률 첫 번째", 60_000L);
        DownAuction tiedSecond = persistDown(seller, "동률 두 번째", 60_000L);
        DownAuction lower = persistDown(seller, "낮은 가격", 60_000L);
        DownAuction higher = persistDown(seller, "높은 가격", 60_000L);

        setAuctionStartedAt(tiedFirst, SNAPSHOT_AT.minusMinutes(15));
        setAuctionStartedAt(tiedSecond, SNAPSHOT_AT.minusMinutes(15));
        setAuctionStartedAt(lower, SNAPSHOT_AT.minusMinutes(20));
        setAuctionStartedAt(higher, SNAPSHOT_AT.minusMinutes(10));
        captureSnapshot();

        assertThat(snapshotQueryRepository.findPage(
                SNAPSHOT_AT,
                AuctionSort.PRICE_LOW,
                null,
                0,
                10
        )).extracting(AuctionPriceSnapshot::auctionId)
                .containsExactly(
                        lower.getId(),
                        tiedSecond.getId(),
                        tiedFirst.getId(),
                        higher.getId()
                );

        assertThat(snapshotQueryRepository.findPage(
                SNAPSHOT_AT,
                AuctionSort.PRICE_HIGH,
                null,
                0,
                10
        )).extracting(AuctionPriceSnapshot::auctionId)
                .containsExactly(
                        higher.getId(),
                        tiedSecond.getId(),
                        tiedFirst.getId(),
                        lower.getId()
                );
    }

    @Test
    void keyword_유무의_결과와_count가_일치하고_LIKE_예약문자를_escape한다() {
        Member seller = persistMember("keyword-seller");
        DownAuction percent = persistDown(seller, "alpha 50% 할인", 60_000L);
        DownAuction underscore = persistDown(seller, "alpha code_name", 60_000L);
        DownAuction exclamation = persistDown(seller, "beta 느낌표!", 60_000L);
        DownAuction wildcardLookalike = persistDown(seller, "alpha 50x 할인", 60_000L);
        DownAuction underscoreLookalike = persistDown(seller, "alpha codeXname", 60_000L);
        DownAuction exclamationLookalike = persistDown(seller, "beta 느낌표X", 60_000L);

        for (DownAuction auction : List.of(
                percent,
                underscore,
                exclamation,
                wildcardLookalike,
                underscoreLookalike,
                exclamationLookalike
        )) {
            setAuctionStartedAt(auction, SNAPSHOT_AT.minusMinutes(5));
        }
        captureSnapshot();

        List<AuctionPriceSnapshot> all = snapshotQueryRepository.findPage(
                SNAPSHOT_AT,
                AuctionSort.PRICE_LOW,
                null,
                0,
                20
        );
        List<AuctionPriceSnapshot> alpha = snapshotQueryRepository.findPage(
                SNAPSHOT_AT,
                AuctionSort.PRICE_LOW,
                "alpha",
                0,
                20
        );

        assertThat(snapshotQueryRepository.count(SNAPSHOT_AT, null)).isEqualTo(all.size());
        assertThat(snapshotQueryRepository.count(SNAPSHOT_AT, "alpha")).isEqualTo(alpha.size());
        assertThat(alpha).extracting(AuctionPriceSnapshot::auctionId)
                .containsExactly(
                        underscoreLookalike.getId(),
                        wildcardLookalike.getId(),
                        underscore.getId(),
                        percent.getId()
                );

        assertThat(snapshotQueryRepository.count(SNAPSHOT_AT, "%")).isEqualTo(1L);
        assertThat(snapshotQueryRepository.findPage(
                SNAPSHOT_AT,
                AuctionSort.PRICE_LOW,
                "%",
                0,
                20
        )).extracting(AuctionPriceSnapshot::auctionId)
                .containsExactly(percent.getId());

        assertThat(snapshotQueryRepository.count(SNAPSHOT_AT, "_")).isEqualTo(1L);
        assertThat(snapshotQueryRepository.count(SNAPSHOT_AT, "!")).isEqualTo(1L);
    }

    @Test
    void offset과_limit으로_가격순_페이지_경계를_적용한다() {
        Member seller = persistMember("offset-seller");
        DownAuction first = persistDown(seller, "페이지 1", 60_000L);
        DownAuction second = persistDown(seller, "페이지 2", 60_000L);
        DownAuction third = persistDown(seller, "페이지 3", 60_000L);
        DownAuction fourth = persistDown(seller, "페이지 4", 60_000L);
        DownAuction fifth = persistDown(seller, "페이지 5", 60_000L);

        setAuctionStartedAt(first, SNAPSHOT_AT);
        setAuctionStartedAt(second, SNAPSHOT_AT.minusMinutes(5));
        setAuctionStartedAt(third, SNAPSHOT_AT.minusMinutes(10));
        setAuctionStartedAt(fourth, SNAPSHOT_AT.minusMinutes(15));
        setAuctionStartedAt(fifth, SNAPSHOT_AT.minusMinutes(20));
        captureSnapshot();

        assertThat(snapshotQueryRepository.findPage(
                SNAPSHOT_AT,
                AuctionSort.PRICE_LOW,
                null,
                2,
                2
        )).extracting(AuctionPriceSnapshot::auctionId)
                .containsExactly(third.getId(), second.getId());
        assertThat(snapshotQueryRepository.findPage(
                SNAPSHOT_AT,
                AuctionSort.PRICE_LOW,
                null,
                4,
                2
        )).extracting(AuctionPriceSnapshot::auctionId)
                .containsExactly(first.getId());
        assertThat(snapshotQueryRepository.findPage(
                SNAPSHOT_AT,
                AuctionSort.PRICE_LOW,
                null,
                5,
                2
        )).isEmpty();

        assertThat(snapshotQueryRepository.findPage(
                SNAPSHOT_AT,
                AuctionSort.PRICE_HIGH,
                null,
                2,
                2
        )).extracting(AuctionPriceSnapshot::auctionId)
                .containsExactly(third.getId(), fourth.getId());
    }

    private void captureSnapshot() {
        entityManager.flush();
        assertThat(snapshotRepository.capture(SNAPSHOT_AT)).isPositive();
        entityManager.clear();
    }

    private DownAuction persistDown(Member seller, String title, long minimumPrice) {
        DownAuction auction = DownAuction.builder()
                .seller(seller)
                .title(title)
                .description("하향 경매 스냅샷 Querydsl 통합 테스트")
                .status(AuctionStatus.OPEN)
                .category(AuctionCategory.HOUSEHOLD)
                .startPrice(START_PRICE)
                .endedAt(SNAPSHOT_AT.plusDays(1))
                .tradeType(TradeType.DELIVERY)
                .contact("01012345678")
                .minimumPrice(minimumPrice)
                .dropPrice(10_000L)
                .priceDropInterval(5L)
                .build();
        entityManager.persist(auction);
        entityManager.flush();
        return auction;
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

    private void setAuctionStartedAt(Auction auction, LocalDateTime startedAt) {
        entityManager.flush();
        entityManager.createQuery("""
                        UPDATE Auction a
                        SET a.startedAt = :startedAt
                        WHERE a.id = :auctionId
                        """)
                .setParameter("startedAt", startedAt)
                .setParameter("auctionId", auction.getId())
                .executeUpdate();
    }
}
