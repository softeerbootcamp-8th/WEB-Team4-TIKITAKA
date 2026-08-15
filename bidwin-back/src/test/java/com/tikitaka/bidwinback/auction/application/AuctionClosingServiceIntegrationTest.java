package com.tikitaka.bidwinback.auction.application;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = "app.storage.s3.bucket=test-bucket")
class AuctionClosingServiceIntegrationTest {

    @Autowired
    private AuctionClosingService auctionClosingService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final List<Long> auctionIds = new ArrayList<>();
    private final List<Long> memberIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        executeInTransaction(entityManager -> {
            for (Long auctionId : auctionIds) {
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
    void 다른_트랜잭션이_경매를_변경중이면_기다리지_않고_다음_시도에서_마감한다()
            throws Exception {
        // given
        Long auctionId = createEndedOpenAuction();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> lockHolder = executor.submit(() -> holdAuctionLock(
                auctionId,
                locked,
                release
        ));

        try {
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

            // when
            long startedAt = System.nanoTime();
            boolean skipped = auctionClosingService.closeOneCandidate();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startedAt
            );

            // then
            assertAll(
                    () -> assertThat(skipped).isFalse(),
                    () -> assertThat(elapsedMillis).isLessThan(1_000L),
                    () -> assertThat(findStatus(auctionId)).isEqualTo(AuctionStatus.OPEN)
            );

            // when
            release.countDown();
            lockHolder.get(5, TimeUnit.SECONDS);
            boolean closed = auctionClosingService.closeOneCandidate();

            // then
            assertThat(closed).isTrue();
            assertThat(findStatus(auctionId)).isEqualTo(AuctionStatus.UNSOLD);
        } finally {
            release.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Long createEndedOpenAuction() {
        Long auctionId = executeInTransaction(entityManager -> {
            String suffix = UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, 8);
            Member seller = Member.builder()
                    .email("seller-" + suffix + "@example.com")
                    .password("encoded-password")
                    .name("통합테스트")
                    .phoneNumber("01012345678")
                    .nickname("판매" + suffix)
                    .status(MemberStatus.ACTIVE)
                    .build();
            entityManager.persist(seller);

            UpAuction auction = UpAuction.builder()
                    .seller(seller)
                    .title("락 경합 마감 테스트")
                    .description("잠긴 경매를 건너뛰고 재시도하는지 검증")
                    .status(AuctionStatus.OPEN)
                    .category(AuctionCategory.HOUSEHOLD)
                    .startPrice(100_000L)
                    .endedAt(LocalDateTime.now().plusDays(1))
                    .tradeType(TradeType.DELIVERY)
                    .contact("01012345678")
                    .buyNowPrice(300_000L)
                    .build();
            entityManager.persist(auction);
            entityManager.flush();
            entityManager.createNativeQuery("""
                            UPDATE auction
                            SET ended_at = SYSDATE(6) - INTERVAL 1 SECOND
                            WHERE id = :auctionId
                            """)
                    .setParameter("auctionId", auction.getId())
                    .executeUpdate();
            memberIds.add(seller.getId());
            return auction.getId();
        });
        auctionIds.add(auctionId);
        return auctionId;
    }

    private void holdAuctionLock(
            Long auctionId,
            CountDownLatch locked,
            CountDownLatch release
    ) {
        executeInTransaction(entityManager -> {
            entityManager.createNativeQuery("""
                            SELECT id
                            FROM auction
                            WHERE id = :auctionId
                            FOR UPDATE
                            """)
                    .setParameter("auctionId", auctionId)
                    .getSingleResult();
            locked.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("락 유지 스레드가 중단되었습니다.", exception);
            }
            return null;
        });
    }

    private AuctionStatus findStatus(Long auctionId) {
        return executeInTransaction(entityManager -> AuctionStatus.valueOf(
                (String) entityManager.createNativeQuery("""
                                SELECT status
                                FROM auction
                                WHERE id = :auctionId
                                """)
                        .setParameter("auctionId", auctionId)
                        .getSingleResult()
        ));
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
}
