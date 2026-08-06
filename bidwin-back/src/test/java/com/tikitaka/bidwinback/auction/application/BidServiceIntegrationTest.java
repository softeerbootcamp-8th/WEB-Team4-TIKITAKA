package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Bid;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;
import com.tikitaka.bidwinback.auction.domain.enums.TradeType;
import com.tikitaka.bidwinback.global.exception.BusinessException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest(properties = "app.storage.s3.bucket=test-bucket")
class BidServiceIntegrationTest {

    private static final long START_PRICE = 100_000L;
    private static final long FIRST_BID_PRICE = 101_000L;
    private static final long SECOND_BID_PRICE = 102_000L;
    private static final long MAX_UNIT_PRICE = Long.MAX_VALUE - Long.MAX_VALUE % 1_000L;

    @Autowired
    private BidService bidService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final List<Long> auctionIds = new ArrayList<>();
    private final List<Long> memberIds = new ArrayList<>();
    private final AtomicInteger memberSequence = new AtomicInteger();

    private String runId;

    @BeforeEach
    void setUp() {
        runId = UUID.randomUUID().toString().replace("-", "");
    }

    @AfterEach
    void tearDown() {
        executeInTransaction(entityManager -> {
            for (Long auctionId : auctionIds) {
                executeDelete(entityManager,
                        "DELETE FROM bid WHERE auction_id = :id", auctionId);
                executeDelete(entityManager,
                        "DELETE FROM up_auction WHERE auction_id = :id", auctionId);
                executeDelete(entityManager,
                        "DELETE FROM auction WHERE id = :id", auctionId);
            }
            for (Long memberId : memberIds) {
                executeDelete(entityManager,
                        "DELETE FROM member WHERE id = :id", memberId);
            }
            return null;
        });
    }

    @Test
    void 동일한_가격의_동시_입찰은_정확히_한_건만_성공한다() throws Exception {
        Fixture fixture = createFixture(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Attempt> first = executor.submit(attemptAfterBarrier(
                    barrier,
                    fixture.bidderIds().get(0),
                    fixture.auctionId(),
                    FIRST_BID_PRICE
            ));
            Future<Attempt> second = executor.submit(attemptAfterBarrier(
                    barrier,
                    fixture.bidderIds().get(1),
                    fixture.auctionId(),
                    FIRST_BID_PRICE
            ));

            List<Attempt> attempts = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );

            assertThat(attempts).filteredOn(Attempt::succeeded).hasSize(1);
            assertThat(attempts)
                    .filteredOn(attempt -> !attempt.succeeded())
                    .extracting(Attempt::errorCode)
                    .containsExactly(ErrorCode.BID_PRICE_TOO_LOW);
            assertThat(findBidPrices(fixture.auctionId()))
                    .containsExactly(FIRST_BID_PRICE);
            assertThat(findAuctionSnapshot(fixture.auctionId()))
                    .isEqualTo(new AuctionSnapshot(
                            FIRST_BID_PRICE,
                            AuctionStatus.BID_ONGOING
                    ));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 서로_다른_가격의_동시_입찰은_최종_최고가를_보장한다() throws Exception {
        Fixture fixture = createFixture(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Attempt> lower = executor.submit(attemptAfterBarrier(
                    barrier,
                    fixture.bidderIds().get(0),
                    fixture.auctionId(),
                    FIRST_BID_PRICE
            ));
            Future<Attempt> higher = executor.submit(attemptAfterBarrier(
                    barrier,
                    fixture.bidderIds().get(1),
                    fixture.auctionId(),
                    SECOND_BID_PRICE
            ));

            Attempt lowerAttempt = lower.get(10, TimeUnit.SECONDS);
            Attempt higherAttempt = higher.get(10, TimeUnit.SECONDS);
            List<Long> storedPrices = findBidPrices(fixture.auctionId());

            assertThat(higherAttempt.succeeded()).isTrue();
            if (!lowerAttempt.succeeded()) {
                assertThat(lowerAttempt.errorCode()).isEqualTo(ErrorCode.BID_PRICE_TOO_LOW);
            }
            assertThat(storedPrices).hasSizeBetween(1, 2);
            assertThat(storedPrices.getLast()).isEqualTo(SECOND_BID_PRICE);
            assertThat(storedPrices).allMatch(price -> price % 1_000L == 0);
            assertThat(findAuctionSnapshot(fixture.auctionId()).currentPrice())
                    .isEqualTo(SECOND_BID_PRICE);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 서로_다른_경매의_동시_입찰은_모두_성공한다() throws Exception {
        Fixture firstFixture = createFixture(1);
        Fixture secondFixture = createFixture(1);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Attempt> first = executor.submit(attemptAfterBarrier(
                    barrier,
                    firstFixture.bidderIds().getFirst(),
                    firstFixture.auctionId(),
                    FIRST_BID_PRICE
            ));
            Future<Attempt> second = executor.submit(attemptAfterBarrier(
                    barrier,
                    secondFixture.bidderIds().getFirst(),
                    secondFixture.auctionId(),
                    FIRST_BID_PRICE
            ));

            List<Attempt> attempts = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );

            assertThat(attempts).allMatch(Attempt::succeeded);
            assertThat(findBidPrices(firstFixture.auctionId()))
                    .containsExactly(FIRST_BID_PRICE);
            assertThat(findBidPrices(secondFixture.auctionId()))
                    .containsExactly(FIRST_BID_PRICE);
            assertThat(findAuctionSnapshot(firstFixture.auctionId()).currentPrice())
                    .isEqualTo(FIRST_BID_PRICE);
            assertThat(findAuctionSnapshot(secondFixture.auctionId()).currentPrice())
                    .isEqualTo(FIRST_BID_PRICE);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 저장된_입찰가는_모두_천원_단위다() {
        Fixture fixture = createFixture(1);
        Long bidderId = fixture.bidderIds().getFirst();

        bidService.place(bidderId, fixture.auctionId(), BidStatus.UP, FIRST_BID_PRICE);
        bidService.place(bidderId, fixture.auctionId(), BidStatus.UP, SECOND_BID_PRICE);
        Throwable thrown = catchThrowable(
                () -> bidService.place(
                        bidderId,
                        fixture.auctionId(),
                        BidStatus.UP,
                        102_500L
                )
        );

        assertThat(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_BID_UNIT)
        );
        assertThat(findBidPrices(fixture.auctionId()))
                .containsExactly(FIRST_BID_PRICE, SECOND_BID_PRICE)
                .allMatch(price -> price % 1_000L == 0);
        assertThat(findAuctionSnapshot(fixture.auctionId()).currentPrice())
                .isEqualTo(SECOND_BID_PRICE);
    }

    @Test
    void 판매자는_자신의_경매에_입찰할_수_없다() {
        Fixture fixture = createFixture(1);

        Throwable thrown = catchThrowable(() -> bidService.place(
                fixture.sellerId(),
                fixture.auctionId(),
                BidStatus.UP,
                FIRST_BID_PRICE
        ));

        assertThat(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SELF_BID_NOT_ALLOWED)
        );
        assertThat(findBidPrices(fixture.auctionId())).isEmpty();
        assertThat(findAuctionSnapshot(fixture.auctionId()))
                .isEqualTo(new AuctionSnapshot(START_PRICE, AuctionStatus.OPEN));
    }

    @Test
    void 종료된_경매는_현재가를_갱신하거나_입찰을_남기지_않는다() {
        Fixture fixture = createFixture(1);
        executeInTransaction(entityManager -> {
            entityManager.createNativeQuery("""
                            UPDATE auction
                            SET ended_at = SYSDATE(6) - INTERVAL 1 SECOND
                            WHERE id = :auctionId
                            """)
                    .setParameter("auctionId", fixture.auctionId())
                    .executeUpdate();
            return null;
        });

        Throwable thrown = catchThrowable(() -> bidService.place(
                fixture.bidderIds().getFirst(),
                fixture.auctionId(),
                BidStatus.UP,
                FIRST_BID_PRICE
        ));

        assertThat(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AUCTION_ALREADY_ENDED)
        );
        assertThat(findBidPrices(fixture.auctionId())).isEmpty();
        assertThat(findAuctionSnapshot(fixture.auctionId()).currentPrice())
                .isEqualTo(START_PRICE);
    }

    @Test
    void 일반_입찰_구간에서_SEALED_입찰을_거절한다() {
        Fixture fixture = createFixture(1);

        Throwable thrown = catchThrowable(() -> bidService.place(
                fixture.bidderIds().getFirst(),
                fixture.auctionId(),
                BidStatus.SEALED,
                FIRST_BID_PRICE
        ));

        assertThat(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.BID_PHASE_MISMATCH)
        );
        assertThat(findBidPrices(fixture.auctionId())).isEmpty();
        assertThat(findAuctionSnapshot(fixture.auctionId()).currentPrice())
                .isEqualTo(START_PRICE);
    }

    @Test
    void 밀봉_입찰_구간에서_UP_입찰을_거절한다() {
        Fixture fixture = createFixture(1);
        moveAuctionIntoSealedBidWindow(fixture.auctionId());

        Throwable thrown = catchThrowable(() -> bidService.place(
                fixture.bidderIds().getFirst(),
                fixture.auctionId(),
                BidStatus.UP,
                FIRST_BID_PRICE
        ));

        assertThat(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.BID_PHASE_MISMATCH)
        );
        assertThat(findBidPrices(fixture.auctionId())).isEmpty();
    }

    @Test
    void 밀봉_입찰은_현재가를_노출하지_않고_SEALED로_저장한다() {
        Fixture fixture = createFixture(1);
        moveAuctionIntoSealedBidWindow(fixture.auctionId());

        BidResult result = bidService.place(
                fixture.bidderIds().getFirst(),
                fixture.auctionId(),
                BidStatus.SEALED,
                FIRST_BID_PRICE
        );

        assertThat(result.status()).isEqualTo(BidStatus.SEALED);
        assertThat(findBidStatuses(fixture.auctionId()))
                .containsExactly(BidStatus.SEALED);
        assertThat(findAuctionSnapshot(fixture.auctionId()))
                .isEqualTo(new AuctionSnapshot(
                        START_PRICE,
                        AuctionStatus.BID_ONGOING
                ));
    }

    @Test
    void 기존_현재가가_null이면_입찰_이력의_최고가를_기준으로_갱신한다() {
        Fixture fixture = createFixture(1);
        long legacyHighestPrice = 105_000L;
        executeInTransaction(entityManager -> {
            UpAuction auction = entityManager.find(UpAuction.class, fixture.auctionId());
            Member bidder = entityManager.getReference(
                    Member.class,
                    fixture.bidderIds().getFirst()
            );
            entityManager.persist(Bid.builder()
                    .auction(auction)
                    .bidder(bidder)
                    .price(legacyHighestPrice)
                    .status(BidStatus.UP)
                    .build());
            entityManager.flush();
            entityManager.createNativeQuery("""
                            UPDATE auction
                            SET current_price = NULL,
                                status = 'BID_ONGOING'
                            WHERE id = :auctionId
                            """)
                    .setParameter("auctionId", fixture.auctionId())
                    .executeUpdate();
            return null;
        });

        Throwable rejected = catchThrowable(() -> bidService.place(
                fixture.bidderIds().getFirst(),
                fixture.auctionId(),
                BidStatus.UP,
                legacyHighestPrice
        ));
        BidResult accepted = bidService.place(
                fixture.bidderIds().getFirst(),
                fixture.auctionId(),
                BidStatus.UP,
                legacyHighestPrice + 1_000L
        );

        assertThat(rejected).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.BID_PRICE_TOO_LOW)
        );
        assertThat(accepted.price()).isEqualTo(legacyHighestPrice + 1_000L);
        assertThat(findAuctionSnapshot(fixture.auctionId()).currentPrice())
                .isEqualTo(legacyHighestPrice + 1_000L);
    }

    @Test
    void 현재가가_bigint_상한에_가까워도_오버플로_없이_입찰을_거절한다() {
        Fixture fixture = createFixture(1);
        executeInTransaction(entityManager -> {
            entityManager.createNativeQuery("""
                            UPDATE auction
                            SET current_price = :currentPrice,
                                status = 'BID_ONGOING'
                            WHERE id = :auctionId
                            """)
                    .setParameter("currentPrice", MAX_UNIT_PRICE)
                    .setParameter("auctionId", fixture.auctionId())
                    .executeUpdate();
            return null;
        });

        Throwable thrown = catchThrowable(() -> bidService.place(
                fixture.bidderIds().getFirst(),
                fixture.auctionId(),
                BidStatus.UP,
                MAX_UNIT_PRICE
        ));

        assertThat(thrown).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.BID_PRICE_TOO_LOW)
        );
        assertThat(findBidPrices(fixture.auctionId())).isEmpty();
        assertThat(findAuctionSnapshot(fixture.auctionId()))
                .isEqualTo(new AuctionSnapshot(
                        MAX_UNIT_PRICE,
                        AuctionStatus.BID_ONGOING
                ));
    }

    @Test
    void 조건부_갱신이_경매_락을_3초_안에_획득하지_못하면_409이고_입찰을_남기지_않는다()
            throws Exception {
        Fixture fixture = createFixture(1);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Void> lockHolder = executor.submit(() -> {
            holdAuctionLock(fixture.auctionId(), lockAcquired, releaseLock);
            return null;
        });

        try {
            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
            long startedAt = System.nanoTime();

            Throwable thrown = catchThrowable(() -> bidService.place(
                    fixture.bidderIds().getFirst(),
                    fixture.auctionId(),
                    BidStatus.UP,
                    FIRST_BID_PRICE
            ));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startedAt
            );

            assertThat(thrown).isInstanceOfSatisfying(
                    BusinessException.class,
                    exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.CONCURRENT_BID_CONFLICT)
            );
            assertThat(elapsedMillis).isBetween(2_500L, 6_000L);
            assertThat(findBidPrices(fixture.auctionId())).isEmpty();
            assertThat(findAuctionSnapshot(fixture.auctionId()).currentPrice())
                    .isEqualTo(START_PRICE);
        } finally {
            releaseLock.countDown();
            lockHolder.get(10, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    private Callable<Attempt> attemptAfterBarrier(
            CyclicBarrier barrier,
            Long memberId,
            Long auctionId,
            long price
    ) {
        return () -> {
            barrier.await(5, TimeUnit.SECONDS);
            try {
                return Attempt.success(
                        price,
                        bidService.place(memberId, auctionId, BidStatus.UP, price)
                );
            } catch (BusinessException exception) {
                return Attempt.failure(price, exception.getErrorCode());
            }
        };
    }

    private void holdAuctionLock(
            Long auctionId,
            CountDownLatch lockAcquired,
            CountDownLatch releaseLock
    ) throws InterruptedException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();

        try {
            transaction.begin();
            entityManager.createNativeQuery("""
                            SELECT id
                            FROM auction
                            WHERE id = :auctionId
                            FOR UPDATE
                            """)
                    .setParameter("auctionId", auctionId)
                    .getSingleResult();
            lockAcquired.countDown();
            // 실패 시에도 테스트가 DB 기본 락 대기 시간만큼 멈추지 않도록 안전 상한을 둔다.
            releaseLock.await(8, TimeUnit.SECONDS);
            transaction.commit();
        } catch (RuntimeException | Error exception) {
            rollback(transaction);
            throw exception;
        } finally {
            entityManager.close();
        }
    }

    private Fixture createFixture(int bidderCount) {
        Fixture fixture = executeInTransaction(entityManager -> {
            Member seller = persistMember(entityManager);
            List<Member> bidders = new ArrayList<>();
            for (int index = 0; index < bidderCount; index++) {
                bidders.add(persistMember(entityManager));
            }

            UpAuction auction = UpAuction.builder()
                    .seller(seller)
                    .title("동시 입찰 통합테스트 " + runId.substring(0, 6))
                    .description("상향 경매 동시 입찰 제어 검증")
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

    private void moveAuctionIntoSealedBidWindow(Long auctionId) {
        executeInTransaction(entityManager -> {
            entityManager.createNativeQuery("""
                            UPDATE auction
                            SET ended_at = CURRENT_TIMESTAMP(6) + INTERVAL 4 MINUTE
                            WHERE id = :auctionId
                            """)
                    .setParameter("auctionId", auctionId)
                    .executeUpdate();
            return null;
        });
    }

    private Member persistMember(EntityManager entityManager) {
        int sequence = memberSequence.incrementAndGet();
        String identifier = runId.substring(0, 6) + sequence;
        Member member = Member.builder()
                .email(identifier + "@example.com")
                .password("encoded-password")
                .name("통합테스트")
                .phoneNumber("01012345678")
                .nickname(identifier)
                .status(MemberStatus.ACTIVE)
                .build();
        entityManager.persist(member);
        return member;
    }

    private List<Long> findBidPrices(Long auctionId) {
        return executeInTransaction(entityManager -> entityManager.createQuery("""
                        select bid.price
                        from Bid bid
                        where bid.auction.id = :auctionId
                        order by bid.price
                        """, Long.class)
                .setParameter("auctionId", auctionId)
                .getResultList());
    }

    private List<BidStatus> findBidStatuses(Long auctionId) {
        return executeInTransaction(entityManager -> entityManager.createQuery("""
                        select bid.status
                        from Bid bid
                        where bid.auction.id = :auctionId
                        order by bid.id
                        """, BidStatus.class)
                .setParameter("auctionId", auctionId)
                .getResultList());
    }

    private AuctionSnapshot findAuctionSnapshot(Long auctionId) {
        return executeInTransaction(entityManager -> {
            Object[] row = entityManager.createQuery("""
                            select auction.currentPrice, auction.status
                            from Auction auction
                            where auction.id = :auctionId
                            """, Object[].class)
                    .setParameter("auctionId", auctionId)
                    .getSingleResult();
            return new AuctionSnapshot(
                    ((Number) row[0]).longValue(),
                    (AuctionStatus) row[1]
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
            rollback(transaction);
            throw exception;
        } finally {
            entityManager.close();
        }
    }

    private void rollback(EntityTransaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }

    private record Fixture(
            Long sellerId,
            List<Long> bidderIds,
            Long auctionId
    ) {
    }

    private record AuctionSnapshot(
            long currentPrice,
            AuctionStatus status
    ) {
    }

    private record Attempt(
            long price,
            BidResult result,
            ErrorCode errorCode
    ) {

        private static Attempt success(long price, BidResult result) {
            return new Attempt(price, result, null);
        }

        private static Attempt failure(long price, ErrorCode errorCode) {
            return new Attempt(price, null, errorCode);
        }

        private boolean succeeded() {
            return result != null;
        }
    }
}
