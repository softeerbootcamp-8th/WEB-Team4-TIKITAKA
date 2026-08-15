package com.tikitaka.bidwinback.auction.domain.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tikitaka.bidwinback.auction.domain.DownAuctionCurrentPriceCalculator;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.DownPriceSnapshot;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.TradeType;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.tikitaka.bidwinback.auction.domain.entity.QDownPriceSnapshot.downPriceSnapshot;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.storage.s3.bucket=test-bucket",
        "app.auction.down-price-snapshot-interval=1h"
})
@Transactional
class DownPriceSnapshotRepositoryIntegrationTest {

    private static final LocalDateTime SNAPSHOT_AT =
            LocalDateTime.of(2000, 1, 1, 12, 0);

    @Autowired
    private DownPriceSnapshotRepository downPriceSnapshotRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void capture한_가격은_계산기와_일치하고_같은_세대의_auction_id가_중복되지_않는다() {
        Member seller = persistMember("capture-price-seller");
        List<DownAuction> auctions = List.of(
                persistDown(
                        seller,
                        "시작 직후",
                        100_000L,
                        40_000L,
                        10_000L,
                        10L,
                        SNAPSHOT_AT.minusSeconds(30),
                        SNAPSHOT_AT.plusHours(1)
                ),
                persistDown(
                        seller,
                        "중간 가격",
                        100_000L,
                        40_000L,
                        10_000L,
                        10L,
                        SNAPSHOT_AT.minusMinutes(25),
                        SNAPSHOT_AT.plusHours(1)
                ),
                persistDown(
                        seller,
                        "최저가 도달 후",
                        100_000L,
                        40_000L,
                        10_000L,
                        10L,
                        SNAPSHOT_AT.minusMinutes(70),
                        SNAPSHOT_AT.plusHours(1)
                ),
                persistDown(
                        seller,
                        "나누어떨어지지 않는 가격 범위",
                        100_000L,
                        75_000L,
                        10_000L,
                        10L,
                        SNAPSHOT_AT.minusMinutes(20),
                        SNAPSHOT_AT.plusHours(1)
                )
        );
        entityManager.flush();
        entityManager.clear();

        int captured = downPriceSnapshotRepository.capture(SNAPSHOT_AT);
        List<DownPriceSnapshot> snapshots = findSnapshots();

        assertThat(captured).isEqualTo(auctions.size());
        assertThat(snapshots).hasSize(auctions.size());
        assertThat(snapshots)
                .extracting(DownPriceSnapshot::getSnapshotAt)
                .containsOnly(SNAPSHOT_AT);
        assertThat(snapshots)
                .extracting(DownPriceSnapshot::getAuctionId)
                .doesNotHaveDuplicates();

        Map<Long, DownAuction> auctionsById = auctions.stream()
                .map(auction -> entityManager.find(DownAuction.class, auction.getId()))
                .collect(Collectors.toMap(DownAuction::getId, Function.identity()));

        assertThat(snapshots).allSatisfy(snapshot -> {
            DownAuction auction = auctionsById.get(snapshot.getAuctionId());
            assertThat(auction).isNotNull();
            long expected = DownAuctionCurrentPriceCalculator.calculate(
                    auction.getStartPrice(),
                    auction.getMinimumPrice(),
                    auction.getDropPrice(),
                    auction.getPriceDropInterval(),
                    auction.getStartedAt(),
                    SNAPSHOT_AT
            );
            assertThat(snapshot.getPrice()).isEqualTo(expected);
        });
    }

    @Test
    void capture는_미시작_마감_완료된_경매를_제외한다() {
        Member seller = persistMember("capture-filter-seller");
        DownAuction active = persistDown(
                seller,
                "진행 중",
                100_000L,
                40_000L,
                10_000L,
                10L,
                SNAPSHOT_AT.minusMinutes(10),
                SNAPSHOT_AT.plusHours(1)
        );
        persistDown(
                seller,
                "미시작",
                100_000L,
                40_000L,
                10_000L,
                10L,
                SNAPSHOT_AT.plusMinutes(1),
                SNAPSHOT_AT.plusHours(1)
        );
        persistDown(
                seller,
                "마감",
                100_000L,
                40_000L,
                10_000L,
                10L,
                SNAPSHOT_AT.minusMinutes(10),
                SNAPSHOT_AT
        );
        DownAuction completed = persistDown(
                seller,
                "완료",
                100_000L,
                40_000L,
                10_000L,
                10L,
                SNAPSHOT_AT.minusMinutes(10),
                SNAPSHOT_AT.plusHours(1)
        );
        setCompletedAt(completed.getId(), SNAPSHOT_AT.minusMinutes(1));
        entityManager.flush();
        entityManager.clear();

        int captured = downPriceSnapshotRepository.capture(SNAPSHOT_AT);
        List<DownPriceSnapshot> snapshots = findSnapshots();

        assertThat(captured).isEqualTo(1);
        assertThat(snapshots)
                .extracting(DownPriceSnapshot::getAuctionId)
                .containsExactly(active.getId());
    }

    @Test
    void deleteOlderThan은_기준시각보다_오래된_세대만_삭제한다() {
        Member seller = persistMember("cleanup-seller");
        persistDown(
                seller,
                "정리 대상",
                100_000L,
                40_000L,
                10_000L,
                10L,
                SNAPSHOT_AT.minusMinutes(10),
                SNAPSHOT_AT.plusHours(1)
        );
        entityManager.flush();
        entityManager.clear();
        LocalDateTime olderSnapshotAt = SNAPSHOT_AT.minusMinutes(1);
        assertThat(downPriceSnapshotRepository.capture(olderSnapshotAt)).isEqualTo(1);
        assertThat(downPriceSnapshotRepository.capture(SNAPSHOT_AT)).isEqualTo(1);

        int deleted = downPriceSnapshotRepository.deleteOlderThan(SNAPSHOT_AT);

        assertThat(deleted).isEqualTo(1);
        assertThat(findSnapshots(olderSnapshotAt)).isEmpty();
        assertThat(findSnapshots(SNAPSHOT_AT)).hasSize(1);
    }

    private List<DownPriceSnapshot> findSnapshots() {
        return findSnapshots(SNAPSHOT_AT);
    }

    private List<DownPriceSnapshot> findSnapshots(LocalDateTime snapshotAt) {
        return new JPAQueryFactory(entityManager)
                .selectFrom(downPriceSnapshot)
                .where(downPriceSnapshot.snapshotAt.eq(snapshotAt))
                .fetch();
    }

    private DownAuction persistDown(
            Member seller,
            String title,
            long startPrice,
            long minimumPrice,
            long dropPrice,
            long priceDropInterval,
            LocalDateTime startedAt,
            LocalDateTime endedAt
    ) {
        DownAuction auction = DownAuction.builder()
                .seller(seller)
                .title(title)
                .description("하향 경매 가격 스냅샷 통합 테스트")
                .status(AuctionStatus.OPEN)
                .category(AuctionCategory.HOUSEHOLD)
                .startPrice(startPrice)
                .endedAt(endedAt)
                .tradeType(TradeType.DELIVERY)
                .contact("01012345678")
                .minimumPrice(minimumPrice)
                .dropPrice(dropPrice)
                .priceDropInterval(priceDropInterval)
                .build();
        entityManager.persist(auction);
        entityManager.flush();
        setStartedAt(auction.getId(), startedAt);
        return auction;
    }

    private void setStartedAt(Long auctionId, LocalDateTime startedAt) {
        entityManager.createQuery("""
                        UPDATE Auction auction
                        SET auction.startedAt = :startedAt
                        WHERE auction.id = :auctionId
                        """)
                .setParameter("startedAt", startedAt)
                .setParameter("auctionId", auctionId)
                .executeUpdate();
    }

    private void setCompletedAt(Long auctionId, LocalDateTime completedAt) {
        entityManager.createQuery("""
                        UPDATE Auction auction
                        SET auction.completedAt = :completedAt
                        WHERE auction.id = :auctionId
                        """)
                .setParameter("completedAt", completedAt)
                .setParameter("auctionId", auctionId)
                .executeUpdate();
    }

    private Member persistMember(String prefix) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);
        Member seller = Member.builder()
                .email(prefix + "-" + suffix + "@example.com")
                .password("encoded-password")
                .name("통합테스트")
                .phoneNumber("01012345678")
                .nickname("n" + suffix)
                .status(MemberStatus.ACTIVE)
                .build();
        entityManager.persist(seller);
        return seller;
    }
}
