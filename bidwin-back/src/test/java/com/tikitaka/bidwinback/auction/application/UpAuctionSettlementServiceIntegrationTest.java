package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.BidType;
import com.tikitaka.bidwinback.auction.domain.enums.TradeType;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.storage.s3.bucket=test-bucket")
class UpAuctionSettlementServiceIntegrationTest {

    private static final long START_PRICE = 100_000L;
    private static final long DEPOSIT_AMOUNT = 30_000L;

    @Autowired
    private BidService bidService;

    @Autowired
    private UpAuctionSettlementService settlementService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final List<Long> auctionIds = new ArrayList<>();
    private final List<Long> memberIds = new ArrayList<>();
    private String runId;

    @BeforeEach
    void setUp() {
        runId = UUID.randomUUID().toString().replace("-", "");
    }

    @AfterEach
    void tearDown() {
        executeInTransaction(entityManager -> {
            for (Long auctionId : auctionIds) {
                delete(entityManager, "DELETE FROM auction_trade WHERE auction_id = :id", auctionId);
                delete(entityManager, "DELETE FROM sealed_bid WHERE auction_id = :id", auctionId);
                delete(entityManager, "DELETE FROM bid WHERE auction_id = :id", auctionId);
                delete(entityManager, "DELETE FROM auction_deposit WHERE auction_id = :id", auctionId);
                delete(entityManager, "DELETE FROM up_auction WHERE auction_id = :id", auctionId);
                delete(entityManager, "DELETE FROM auction WHERE id = :id", auctionId);
            }
            for (Long memberId : memberIds) {
                delete(entityManager, "DELETE FROM member WHERE id = :id", memberId);
            }
            return null;
        });
    }

    @Test
    void 일반입찰과_밀봉입찰을_비교해_낙찰하고_보증금은_유지한다() {
        Fixture fixture = createFixture(2);
        Long openBidderId = fixture.bidderIds().get(0);
        Long sealedBidderId = fixture.bidderIds().get(1);
        bidService.place(openBidderId, fixture.auctionId(), 101_000L, BidType.OPEN);
        moveEndedAt(fixture.auctionId(), "SYSDATE(6) + INTERVAL 2 MINUTE");
        bidService.place(
                sealedBidderId,
                fixture.auctionId(),
                105_000L,
                BidType.SEALED
        );
        moveEndedAt(fixture.auctionId(), "SYSDATE(6) - INTERVAL 1 SECOND");

        UpAuctionSettlementResult result = settlementService.settle(fixture.auctionId());

        assertThat(result.status()).isEqualTo(AuctionStatus.COMPLETED);
        assertThat(result.winnerId()).isEqualTo(sealedBidderId);
        assertThat(result.finalPrice()).isEqualTo(105_000L);
        assertThat(findAuctionSnapshot(fixture.auctionId()))
                .isEqualTo(new AuctionSnapshot(AuctionStatus.COMPLETED, 105_000L));
        assertThat(findTradeCount(fixture.auctionId())).isEqualTo(1L);
        assertThat(findDepositCount(fixture.auctionId())).isEqualTo(2L);
        assertThat(findPoints(openBidderId))
                .isEqualTo(new Points(2_000_000L - DEPOSIT_AMOUNT, DEPOSIT_AMOUNT));
        assertThat(findPoints(sealedBidderId))
                .isEqualTo(new Points(2_000_000L - DEPOSIT_AMOUNT, DEPOSIT_AMOUNT));
    }

    @Test
    void 입찰이_없으면_유찰되고_재실행해도_부작용이_없다() {
        Fixture fixture = createFixture(1);
        moveEndedAt(fixture.auctionId(), "SYSDATE(6) - INTERVAL 1 SECOND");

        UpAuctionSettlementResult first = settlementService.settle(fixture.auctionId());
        UpAuctionSettlementResult second = settlementService.settle(fixture.auctionId());

        assertThat(first.status()).isEqualTo(AuctionStatus.UNSOLD);
        assertThat(second).isEqualTo(first);
        assertThat(findTradeCount(fixture.auctionId())).isZero();
        assertThat(findAuctionSnapshot(fixture.auctionId()).status())
                .isEqualTo(AuctionStatus.UNSOLD);
    }

    @Test
    void 동시에_정산해도_거래는_한_건만_생성된다() throws Exception {
        Fixture fixture = createFixture(1);
        bidService.place(
                fixture.bidderIds().getFirst(),
                fixture.auctionId(),
                101_000L,
                BidType.OPEN
        );
        moveEndedAt(fixture.auctionId(), "SYSDATE(6) - INTERVAL 1 SECOND");
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<UpAuctionSettlementResult> first = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return settlementService.settle(fixture.auctionId());
            });
            Future<UpAuctionSettlementResult> second = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return settlementService.settle(fixture.auctionId());
            });

            assertThat(first.get(10, TimeUnit.SECONDS).winnerId())
                    .isEqualTo(fixture.bidderIds().getFirst());
            assertThat(second.get(10, TimeUnit.SECONDS).winnerId())
                    .isEqualTo(fixture.bidderIds().getFirst());
            assertThat(findTradeCount(fixture.auctionId())).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private Fixture createFixture(int bidderCount) {
        Fixture fixture = executeInTransaction(entityManager -> {
            Member seller = persistMember(entityManager, "seller");
            List<Member> bidders = new ArrayList<>();
            for (int index = 0; index < bidderCount; index++) {
                bidders.add(persistMember(entityManager, "bidder" + index));
            }
            UpAuction auction = UpAuction.builder()
                    .seller(seller)
                    .title("상향경매 정산 통합테스트")
                    .description("일반입찰과 밀봉입찰 정산 검증")
                    .status(AuctionStatus.OPEN)
                    .category(AuctionCategory.HOUSEHOLD)
                    .startPrice(START_PRICE)
                    .endedAt(LocalDateTime.now().plusDays(1))
                    .tradeType(TradeType.DELIVERY)
                    .contact("01012345678")
                    .buyNowPrice(300_000L)
                    .build();
            entityManager.persist(auction);
            entityManager.flush();
            return new Fixture(
                    seller.getId(),
                    bidders.stream().map(Member::getId).toList(),
                    auction.getId()
            );
        });
        memberIds.add(fixture.sellerId());
        memberIds.addAll(fixture.bidderIds());
        auctionIds.add(fixture.auctionId());
        return fixture;
    }

    private Member persistMember(EntityManager entityManager, String role) {
        String identifier = runId.substring(0, 8) + role;
        String nickname = (role + runId).substring(0, 10);
        Member member = Member.builder()
                .email(identifier + "@example.com")
                .password("encoded-password")
                .name("통합테스트")
                .phoneNumber("01012345678")
                .nickname(nickname)
                .status(MemberStatus.ACTIVE)
                .build();
        entityManager.persist(member);
        return member;
    }

    private void moveEndedAt(Long auctionId, String databaseExpression) {
        if (!databaseExpression.equals("SYSDATE(6) + INTERVAL 2 MINUTE")
                && !databaseExpression.equals("SYSDATE(6) - INTERVAL 1 SECOND")) {
            throw new IllegalArgumentException("허용되지 않은 테스트 시각 표현식입니다.");
        }
        executeInTransaction(entityManager -> {
            entityManager.createNativeQuery("UPDATE auction SET ended_at = "
                            + databaseExpression + " WHERE id = :auctionId")
                    .setParameter("auctionId", auctionId)
                    .executeUpdate();
            return null;
        });
    }

    private AuctionSnapshot findAuctionSnapshot(Long auctionId) {
        return executeInTransaction(entityManager -> {
            Object[] row = (Object[]) entityManager.createNativeQuery("""
                            SELECT status, current_price
                            FROM auction
                            WHERE id = :auctionId
                            """)
                    .setParameter("auctionId", auctionId)
                    .getSingleResult();
            return new AuctionSnapshot(
                    AuctionStatus.valueOf((String) row[0]),
                    ((Number) row[1]).longValue()
            );
        });
    }

    private long findTradeCount(Long auctionId) {
        return count("auction_trade", auctionId);
    }

    private long findDepositCount(Long auctionId) {
        return count("auction_deposit", auctionId);
    }

    private long count(String table, Long auctionId) {
        if (!table.equals("auction_trade") && !table.equals("auction_deposit")) {
            throw new IllegalArgumentException("허용되지 않은 테스트 테이블입니다.");
        }
        return executeInTransaction(entityManager -> {
            Number count = (Number) entityManager.createNativeQuery(
                            "SELECT COUNT(*) FROM " + table + " WHERE auction_id = :auctionId"
                    )
                    .setParameter("auctionId", auctionId)
                    .getSingleResult();
            return count.longValue();
        });
    }

    private Points findPoints(Long memberId) {
        return executeInTransaction(entityManager -> {
            Object[] row = (Object[]) entityManager.createNativeQuery("""
                            SELECT total_point, locked_point
                            FROM member
                            WHERE id = :memberId
                            """)
                    .setParameter("memberId", memberId)
                    .getSingleResult();
            return new Points(
                    ((Number) row[0]).longValue(),
                    ((Number) row[1]).longValue()
            );
        });
    }

    private void delete(EntityManager entityManager, String sql, Long id) {
        entityManager.createNativeQuery(sql)
                .setParameter("id", id)
                .executeUpdate();
    }

    private <T> T executeInTransaction(Function<EntityManager, T> action) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            T result = action.apply(entityManager);
            transaction.commit();
            return result;
        } catch (RuntimeException | Error exception) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw exception;
        } finally {
            entityManager.close();
        }
    }

    private record Fixture(Long sellerId, List<Long> bidderIds, Long auctionId) {
    }

    private record AuctionSnapshot(AuctionStatus status, long currentPrice) {
    }

    private record Points(long totalPoint, long lockedPoint) {
    }
}
