package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionDeposit;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest(properties = "app.storage.s3.bucket=test-bucket")
class DepositSettlementServiceIntegrationTest {

    private static final long INITIAL_RESERVED_AMOUNT = 30_000L;
    private static final long TARGET_AMOUNT = 150_000L;
    private static final long INITIAL_TOTAL_POINT = 1_970_000L;
    private static final long INITIAL_SELLER_POINT = 2_000_000L;

    @Autowired
    private DepositSettlementService depositSettlementService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Long auctionId;
    private Long sellerId;
    private Long buyerId;
    private String runId;

    @BeforeEach
    void setUp() {
        runId = UUID.randomUUID().toString().replace("-", "");
        createFixture();
    }

    @AfterEach
    void tearDown() {
        executeInTransaction(entityManager -> {
            executeDelete(entityManager,
                    "DELETE FROM auction_deposit WHERE auction_id = :id", auctionId);
            executeDelete(entityManager,
                    "DELETE FROM up_auction WHERE auction_id = :id", auctionId);
            executeDelete(entityManager,
                    "DELETE FROM auction WHERE id = :id", auctionId);
            executeDelete(entityManager,
                    "DELETE FROM member WHERE id = :id", buyerId);
            executeDelete(entityManager,
                    "DELETE FROM member WHERE id = :id", sellerId);
            return null;
        });
    }

    @Test
    void 동일한_보증금을_동시에_상향하면_두_요청이_성공하고_차액은_한_번만_잠긴다() throws Exception {
        // given
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // when
            Future<DepositFundingResult> first = executor.submit(() -> {
                barrier.await();
                return depositSettlementService.topUpToFinalPrice(
                        auctionId, buyerId, TARGET_AMOUNT);
            });
            Future<DepositFundingResult> second = executor.submit(() -> {
                barrier.await();
                return depositSettlementService.topUpToFinalPrice(
                        auctionId, buyerId, TARGET_AMOUNT);
            });

            List<DepositFundingResult> results = List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );

            // then
            assertThat(results)
                    .extracting(DepositFundingResult::addedAmount)
                    .containsExactlyInAnyOrder(TARGET_AMOUNT - INITIAL_RESERVED_AMOUNT, 0L);
            assertThat(findSnapshot()).isEqualTo(new DepositSnapshot(
                    TARGET_AMOUNT,
                    INITIAL_TOTAL_POINT - (TARGET_AMOUNT - INITIAL_RESERVED_AMOUNT),
                    TARGET_AMOUNT,
                    INITIAL_SELLER_POINT,
                    "HELD"
            ));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 보증금을_몰수하면_구매자_잠금액이_판매자에게_지급된다() {
        // given

        // when
        depositSettlementService.forfeit(
                auctionId, buyerId, sellerId, INITIAL_RESERVED_AMOUNT);

        // then
        assertThat(findSnapshot()).isEqualTo(new DepositSnapshot(
                INITIAL_RESERVED_AMOUNT,
                INITIAL_TOTAL_POINT,
                0L,
                INITIAL_SELLER_POINT + INITIAL_RESERVED_AMOUNT,
                "FORFEITED"
        ));
    }

    @Test
    void 몰수금을_판매자에게_지급할_수_없으면_보증금과_구매자_잠금액을_유지한다() {
        // given
        long unknownSellerId = Long.MAX_VALUE;

        // when
        Throwable exception = catchThrowable(() -> depositSettlementService.forfeit(
                auctionId, buyerId, unknownSellerId, INITIAL_RESERVED_AMOUNT));

        // then
        assertThat(exception)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("보증금 몰수 중 판매자 잔액을 지급하지 못했습니다.");
        assertThat(findSnapshot()).isEqualTo(new DepositSnapshot(
                INITIAL_RESERVED_AMOUNT,
                INITIAL_TOTAL_POINT,
                INITIAL_RESERVED_AMOUNT,
                INITIAL_SELLER_POINT,
                "HELD"
        ));
    }

    private void createFixture() {
        executeInTransaction(entityManager -> {
            sellerId = persistMember(entityManager, "s", INITIAL_SELLER_POINT, 0L).getId();
            Member buyer = persistMember(
                    entityManager,
                    "b",
                    INITIAL_TOTAL_POINT,
                    INITIAL_RESERVED_AMOUNT
            );
            buyerId = buyer.getId();

            UpAuction auction = UpAuction.builder()
                    .seller(entityManager.getReference(Member.class, sellerId))
                    .title("보증금 동시성 통합테스트")
                    .description("동일 보증금 동시 상향 검증")
                    .status(AuctionStatus.OPEN)
                    .category(AuctionCategory.HOUSEHOLD)
                    .startPrice(100_000L)
                    .endedAt(LocalDateTime.now().plusDays(1))
                    .tradeType(TradeType.DELIVERY)
                    .contact("01012345678")
                    .buyNowPrice(TARGET_AMOUNT)
                    .build();
            entityManager.persist(auction);

            entityManager.persist(AuctionDeposit.builder()
                    .member(buyer)
                    .auction(auction)
                    .reservedAmount(INITIAL_RESERVED_AMOUNT)
                    .build());
            entityManager.flush();
            auctionId = auction.getId();
            return null;
        });
    }

    private Member persistMember(
            EntityManager entityManager,
            String role,
            long totalPoint,
            long lockedPoint
    ) {
        String identifier = runId.substring(0, 6) + role;
        Member member = Member.builder()
                .email(identifier + "@example.com")
                .password("encoded-password")
                .name("통합테스트")
                .phoneNumber("01012345678")
                .nickname(identifier)
                .status(MemberStatus.ACTIVE)
                .totalPoint(totalPoint)
                .lockedPoint(lockedPoint)
                .build();
        entityManager.persist(member);
        entityManager.flush();
        return member;
    }

    private DepositSnapshot findSnapshot() {
        return executeInTransaction(entityManager -> {
            Object[] deposit = (Object[]) entityManager.createNativeQuery("""
                            SELECT reserved_amount, status
                            FROM auction_deposit
                            WHERE auction_id = :auctionId
                              AND member_id = :buyerId
                            """)
                    .setParameter("auctionId", auctionId)
                    .setParameter("buyerId", buyerId)
                    .getSingleResult();
            Object[] buyerPoints = (Object[]) entityManager.createNativeQuery("""
                            SELECT total_point, locked_point
                            FROM member
                            WHERE id = :buyerId
                            """)
                    .setParameter("buyerId", buyerId)
                    .getSingleResult();
            Number sellerPoint = (Number) entityManager.createNativeQuery("""
                            SELECT total_point
                            FROM member
                            WHERE id = :sellerId
                            """)
                    .setParameter("sellerId", sellerId)
                    .getSingleResult();
            return new DepositSnapshot(
                    ((Number) deposit[0]).longValue(),
                    ((Number) buyerPoints[0]).longValue(),
                    ((Number) buyerPoints[1]).longValue(),
                    sellerPoint.longValue(),
                    (String) deposit[1]
            );
        });
    }

    private void executeDelete(EntityManager entityManager, String sql, Long id) {
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

    private record DepositSnapshot(
            long reservedAmount,
            long totalPoint,
            long lockedPoint,
            long sellerPoint,
            String status
    ) {
    }
}
