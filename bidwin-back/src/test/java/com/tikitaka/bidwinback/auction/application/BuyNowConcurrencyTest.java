package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.AuctionDeposit;
import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.DepositStatus;
import com.tikitaka.bidwinback.auction.domain.enums.TradeType;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@SpringBootTest
class BuyNowConcurrencyTest {

    private static final long BUY_NOW_PRICE = 100_000L;
    private static final long INITIAL_BUYER_POINT = 500_000L;

    @Autowired
    private BuyNowService buyNowService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Fixture fixture;

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    @AfterEach
    void tearDown() {
        if (fixture == null) {
            return;
        }

        executeInTransaction(entityManager -> {
            List<Long> auctionIds = List.of(
                    fixture.auctionId(),
                    fixture.downAuctionId()
            );
            entityManager.createQuery("""
                            DELETE FROM InstantPurchaseRequest request
                            WHERE request.auctionId IN :auctionIds
                            """)
                    .setParameter("auctionIds", auctionIds)
                    .executeUpdate();
            entityManager.createQuery("""
                            DELETE FROM AuctionDeposit deposit
                            WHERE deposit.auction.id IN :auctionIds
                            """)
                    .setParameter("auctionIds", auctionIds)
                    .executeUpdate();
            entityManager.createQuery("""
                            DELETE FROM AuctionTrade trade
                            WHERE trade.auction.id IN :auctionIds
                            """)
                    .setParameter("auctionIds", auctionIds)
                    .executeUpdate();

            for (Long auctionId : auctionIds) {
                Auction auction = entityManager.find(Auction.class, auctionId);
                if (auction != null) {
                    entityManager.remove(auction);
                }
            }
            removeMember(entityManager, fixture.firstBuyerId());
            removeMember(entityManager, fixture.secondBuyerId());
            removeMember(entityManager, fixture.sellerId());
            return null;
        });
    }

    @Test
    void 동일한_경매에_동시_즉시구매하면_한_요청만_거래를_확정한다() throws Exception {
        // given
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<PurchaseAttempt> first = executor.submit(
                    purchase(barrier, fixture.firstBuyerId(), UUID.randomUUID().toString())
            );
            Future<PurchaseAttempt> second = executor.submit(
                    purchase(barrier, fixture.secondBuyerId(), UUID.randomUUID().toString())
            );

            // when
            List<PurchaseAttempt> attempts = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );

            // then
            assertThat(attempts).filteredOn(PurchaseAttempt::successful).hasSize(1);
            assertThat(attempts)
                    .filteredOn(attempt -> !attempt.successful())
                    .extracting(PurchaseAttempt::errorCode)
                    .containsExactly(ErrorCode.CONCURRENT_TRADE_CONFLICT);

            PurchaseState state = readPurchaseState();
            assertThat(state.auctionStatus()).isEqualTo(AuctionStatus.COMPLETED);
            assertThat(state.tradeCount()).isEqualTo(1L);
            assertThat(state.depositCount()).isEqualTo(1L);
            assertThat(state.idempotencyRequestCount()).isEqualTo(1L);
            assertThat(state.finalPrice()).isEqualTo(BUY_NOW_PRICE);
            assertThat(state.depositAmount()).isEqualTo(BUY_NOW_PRICE);
            assertThat(state.depositStatus()).isEqualTo(DepositStatus.HELD);
            assertThat(state.tradeBuyerId()).isEqualTo(state.depositMemberId());
            assertThat(state.totalBuyerPoint()).isEqualTo(
                    INITIAL_BUYER_POINT * 2 - BUY_NOW_PRICE
            );
            assertThat(state.totalLockedPoint()).isEqualTo(BUY_NOW_PRICE);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 같은_멱등키로_재시도하면_포인트를_다시_잠그지_않고_같은_거래를_반환한다() {
        // given
        String idempotencyKey = UUID.randomUUID().toString();

        // when
        BuyNowService.BuyNowResult first = buyNowService.purchase(
                fixture.auctionId(),
                fixture.firstBuyerId(),
                idempotencyKey
        );
        BuyNowService.BuyNowResult replay = buyNowService.purchase(
                fixture.auctionId(),
                fixture.firstBuyerId(),
                idempotencyKey
        );

        // then
        assertThat(replay.tradeId()).isEqualTo(first.tradeId());
        assertThat(replay.finalPrice()).isEqualTo(first.finalPrice());
        assertThat(replay.replayed()).isTrue();

        PurchaseState state = readPurchaseState();
        assertThat(state.tradeCount()).isEqualTo(1L);
        assertThat(state.depositCount()).isEqualTo(1L);
        assertThat(state.idempotencyRequestCount()).isEqualTo(1L);
        assertThat(state.totalBuyerPoint()).isEqualTo(
                INITIAL_BUYER_POINT * 2 - BUY_NOW_PRICE
        );
        assertThat(state.totalLockedPoint()).isEqualTo(BUY_NOW_PRICE);
    }

    @Test
    void 같은_멱등키로_동시에_요청해도_두_응답은_하나의_거래를_가리킨다() throws Exception {
        // given
        String idempotencyKey = UUID.randomUUID().toString();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<PurchaseAttempt> first = executor.submit(
                    purchase(barrier, fixture.firstBuyerId(), idempotencyKey)
            );
            Future<PurchaseAttempt> second = executor.submit(
                    purchase(barrier, fixture.firstBuyerId(), idempotencyKey)
            );

            // when
            List<PurchaseAttempt> attempts = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );

            // then
            assertThat(attempts).allMatch(PurchaseAttempt::successful);
            Long tradeId = attempts.getFirst().result().tradeId();
            assertThat(attempts)
                    .extracting(attempt -> attempt.result().tradeId())
                    .containsOnly(tradeId);
            assertThat(attempts)
                    .extracting(attempt -> attempt.result().replayed())
                    .containsExactlyInAnyOrder(false, true);

            PurchaseState state = readPurchaseState();
            assertThat(state.tradeCount()).isEqualTo(1L);
            assertThat(state.depositCount()).isEqualTo(1L);
            assertThat(state.idempotencyRequestCount()).isEqualTo(1L);
            assertThat(state.totalBuyerPoint()).isEqualTo(
                    INITIAL_BUYER_POINT * 2 - BUY_NOW_PRICE
            );
            assertThat(state.totalLockedPoint()).isEqualTo(BUY_NOW_PRICE);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 부모_타입으로_조회한_하향_경매는_DB_시각으로_최종가격을_확정한다() {
        // given
        String idempotencyKey = UUID.randomUUID().toString();

        // when
        BuyNowService.BuyNowResult result = buyNowService.purchase(
                fixture.downAuctionId(),
                fixture.firstBuyerId(),
                idempotencyKey
        );

        // then
        assertThat(result.finalPrice()).isEqualTo(70_000L);
        PurchaseState state = readPurchaseState(fixture.downAuctionId());
        assertThat(state.finalPrice()).isEqualTo(70_000L);
        assertThat(state.depositAmount()).isEqualTo(70_000L);
        assertThat(state.totalLockedPoint()).isEqualTo(70_000L);
    }

    @Test
    void DB_종료시각과_같거나_늦게_도착한_즉시구매는_아무것도_변경하지_않는다() {
        // given
        executeInTransaction(entityManager -> {
            entityManager.createNativeQuery("""
                            UPDATE auction
                            SET ended_at = CURRENT_TIMESTAMP(6)
                            WHERE id = :auctionId
                            """)
                    .setParameter("auctionId", fixture.auctionId())
                    .executeUpdate();
            return null;
        });

        // when
        // then
        assertThatExceptionOfType(AuctionException.class)
                .isThrownBy(() -> buyNowService.purchase(
                        fixture.auctionId(),
                        fixture.firstBuyerId(),
                        UUID.randomUUID().toString()
                ))
                .extracting(AuctionException::getErrorCode)
                .isEqualTo(ErrorCode.AUCTION_ALREADY_ENDED);

        executeInTransaction(entityManager -> {
            Auction auction = entityManager.find(Auction.class, fixture.auctionId());
            Member buyer = entityManager.find(Member.class, fixture.firstBuyerId());
            Long tradeCount = countByAuction(entityManager, "AuctionTrade");
            Long depositCount = countByAuction(entityManager, "AuctionDeposit");
            Long requestCount = entityManager.createQuery("""
                            SELECT COUNT(request)
                            FROM InstantPurchaseRequest request
                            WHERE request.auctionId = :auctionId
                            """, Long.class)
                    .setParameter("auctionId", fixture.auctionId())
                    .getSingleResult();

            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.BID_ONGOING);
            assertThat(tradeCount).isZero();
            assertThat(depositCount).isZero();
            assertThat(requestCount).isZero();
            assertThat(buyer.getTotalPoint()).isEqualTo(INITIAL_BUYER_POINT);
            assertThat(buyer.getLockedPoint()).isZero();
            return null;
        });
    }

    @Test
    void 다른_구매자나_경매는_완료된_멱등키를_재사용할_수_없다() {
        // given
        String idempotencyKey = UUID.randomUUID().toString();
        buyNowService.purchase(
                fixture.auctionId(),
                fixture.firstBuyerId(),
                idempotencyKey
        );

        // when
        // then
        assertThatExceptionOfType(AuctionException.class)
                .isThrownBy(() -> buyNowService.purchase(
                        fixture.downAuctionId(),
                        fixture.secondBuyerId(),
                        idempotencyKey
                ))
                .extracting(AuctionException::getErrorCode)
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);

        executeInTransaction(entityManager -> {
            Auction downAuction = entityManager.find(
                    Auction.class,
                    fixture.downAuctionId()
            );
            Member secondBuyer = entityManager.find(
                    Member.class,
                    fixture.secondBuyerId()
            );
            Long downTradeCount = entityManager.createQuery("""
                            SELECT COUNT(trade)
                            FROM AuctionTrade trade
                            WHERE trade.auction.id = :auctionId
                            """, Long.class)
                    .setParameter("auctionId", fixture.downAuctionId())
                    .getSingleResult();

            assertThat(downAuction.getStatus()).isEqualTo(AuctionStatus.BID_ONGOING);
            assertThat(downTradeCount).isZero();
            assertThat(secondBuyer.getTotalPoint()).isEqualTo(INITIAL_BUYER_POINT);
            assertThat(secondBuyer.getLockedPoint()).isZero();
            return null;
        });
    }

    private Callable<PurchaseAttempt> purchase(
            CyclicBarrier barrier,
            Long buyerId,
            String idempotencyKey
    ) {
        return () -> {
            barrier.await(5, TimeUnit.SECONDS);
            try {
                BuyNowService.BuyNowResult result = buyNowService.purchase(
                        fixture.auctionId(),
                        buyerId,
                        idempotencyKey
                );
                return new PurchaseAttempt(true, result, null);
            } catch (AuctionException exception) {
                return new PurchaseAttempt(false, null, exception.getErrorCode());
            }
        };
    }

    private Fixture createFixture() {
        return executeInTransaction(entityManager -> {
            String identifier = UUID.randomUUID().toString().substring(0, 8);
            Member seller = member(identifier, "s", 2_000_000L);
            Member firstBuyer = member(identifier, "a", INITIAL_BUYER_POINT);
            Member secondBuyer = member(identifier, "b", INITIAL_BUYER_POINT);
            entityManager.persist(seller);
            entityManager.persist(firstBuyer);
            entityManager.persist(secondBuyer);

            UpAuction auction = UpAuction.builder()
                    .seller(seller)
                    .title("동시 즉시구매 테스트")
                    .description("동일 경매는 한 명만 구매한다.")
                    .status(AuctionStatus.BID_ONGOING)
                    .category(AuctionCategory.HOUSEHOLD)
                    .startPrice(50_000L)
                    .endedAt(LocalDateTime.now().plusMinutes(10))
                    .tradeType(TradeType.DELIVERY)
                    .contact("test@example.com")
                    .buyNowPrice(BUY_NOW_PRICE)
                    .build();
            entityManager.persist(auction);

            DownAuction downAuction = DownAuction.builder()
                    .seller(seller)
                    .title("DB 시각 가격 테스트")
                    .description("서버가 DB 시각으로 하향 가격을 계산한다.")
                    .status(AuctionStatus.BID_ONGOING)
                    .category(AuctionCategory.HOUSEHOLD)
                    .startPrice(100_000L)
                    .endedAt(LocalDateTime.now().plusMinutes(10))
                    .tradeType(TradeType.DELIVERY)
                    .contact("test@example.com")
                    .minimumPrice(50_000L)
                    .dropPrice(10_000L)
                    .priceDropInterval(10L)
                    .build();
            entityManager.persist(downAuction);
            entityManager.flush();

            entityManager.createNativeQuery("""
                            UPDATE auction
                            SET created_at = CURRENT_TIMESTAMP(6) - INTERVAL 31 MINUTE
                            WHERE id = :auctionId
                            """)
                    .setParameter("auctionId", downAuction.getId())
                    .executeUpdate();

            return new Fixture(
                    auction.getId(),
                    downAuction.getId(),
                    seller.getId(),
                    firstBuyer.getId(),
                    secondBuyer.getId()
            );
        });
    }

    private Member member(String identifier, String prefix, long totalPoint) {
        return Member.builder()
                .email(prefix + identifier + "@example.com")
                .password("encoded-password")
                .name("즉시구매테스트")
                .phoneNumber("01012345678")
                .nickname(prefix + identifier)
                .status(MemberStatus.ACTIVE)
                .totalPoint(totalPoint)
                .lockedPoint(0L)
                .build();
    }

    private PurchaseState readPurchaseState() {
        return readPurchaseState(fixture.auctionId());
    }

    private PurchaseState readPurchaseState(Long auctionId) {
        return executeInTransaction(entityManager -> {
            Auction auction = entityManager.find(Auction.class, auctionId);
            List<AuctionTrade> trades = entityManager.createQuery("""
                            SELECT trade
                            FROM AuctionTrade trade
                            JOIN FETCH trade.buyer
                            WHERE trade.auction.id = :auctionId
                            """, AuctionTrade.class)
                    .setParameter("auctionId", auctionId)
                    .getResultList();
            List<AuctionDeposit> deposits = entityManager.createQuery("""
                            SELECT deposit
                            FROM AuctionDeposit deposit
                            JOIN FETCH deposit.member
                            WHERE deposit.auction.id = :auctionId
                            """, AuctionDeposit.class)
                    .setParameter("auctionId", auctionId)
                    .getResultList();
            Long idempotencyRequestCount = entityManager.createQuery("""
                            SELECT COUNT(request)
                            FROM InstantPurchaseRequest request
                            WHERE request.auctionId = :auctionId
                            """, Long.class)
                    .setParameter("auctionId", auctionId)
                    .getSingleResult();
            Member firstBuyer = entityManager.find(Member.class, fixture.firstBuyerId());
            Member secondBuyer = entityManager.find(Member.class, fixture.secondBuyerId());

            AuctionTrade trade = trades.getFirst();
            AuctionDeposit deposit = deposits.getFirst();
            return new PurchaseState(
                    auction.getStatus(),
                    (long) trades.size(),
                    (long) deposits.size(),
                    idempotencyRequestCount,
                    trade.getFinalPrice(),
                    trade.getBuyer().getId(),
                    deposit.getReservedAmount(),
                    deposit.getStatus(),
                    deposit.getMember().getId(),
                    firstBuyer.getTotalPoint() + secondBuyer.getTotalPoint(),
                    firstBuyer.getLockedPoint() + secondBuyer.getLockedPoint()
            );
        });
    }

    private void removeMember(EntityManager entityManager, Long memberId) {
        Member member = entityManager.find(Member.class, memberId);
        if (member != null) {
            entityManager.remove(member);
        }
    }

    private Long countByAuction(EntityManager entityManager, String entityName) {
        return entityManager.createQuery("""
                        SELECT COUNT(entity)
                        FROM %s entity
                        WHERE entity.auction.id = :auctionId
                        """.formatted(entityName), Long.class)
                .setParameter("auctionId", fixture.auctionId())
                .getSingleResult();
    }

    private <T> T executeInTransaction(Function<EntityManager, T> action) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();

        try {
            transaction.begin();
            T result = action.apply(entityManager);
            transaction.commit();
            return result;
        } catch (RuntimeException exception) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw exception;
        } finally {
            entityManager.close();
        }
    }

    private record Fixture(
            Long auctionId,
            Long downAuctionId,
            Long sellerId,
            Long firstBuyerId,
            Long secondBuyerId
    ) {
    }

    private record PurchaseAttempt(
            boolean successful,
            BuyNowService.BuyNowResult result,
            ErrorCode errorCode
    ) {
    }

    private record PurchaseState(
            AuctionStatus auctionStatus,
            long tradeCount,
            long depositCount,
            long idempotencyRequestCount,
            long finalPrice,
            Long tradeBuyerId,
            long depositAmount,
            DepositStatus depositStatus,
            Long depositMemberId,
            long totalBuyerPoint,
            long totalLockedPoint
    ) {
    }
}
