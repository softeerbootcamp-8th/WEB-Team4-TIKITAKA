package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.auth.domain.entity.PasswordResetToken;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.OptimisticLockException;
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

@SpringBootTest
class PasswordResetTokenConcurrencyTest {

    private static final String FIRST_PASSWORD = "encoded-first-password";
    private static final String SECOND_PASSWORD = "encoded-second-password";

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Long tokenId;

    @BeforeEach
    void setUp() {
        String identifier = UUID.randomUUID().toString().substring(0, 8);
        tokenId = executeInTransaction(entityManager -> {
            Member member = Member.builder()
                    .email("concurrency-%s@example.com".formatted(identifier))
                    .password("encoded-old-password")
                    .name("동시성테스트")
                    .phoneNumber("01012345678")
                    .nickname(identifier)
                    .build();
            entityManager.persist(member);

            PasswordResetToken token = PasswordResetToken.issue(
                    member,
                    "a".repeat(64),
                    LocalDateTime.now().plusMinutes(5)
            );
            entityManager.persist(token);
            entityManager.flush();
            return token.getId();
        });
    }

    @AfterEach
    void tearDown() {
        if (tokenId == null) {
            return;
        }

        executeInTransaction(entityManager -> {
            PasswordResetToken token = entityManager.find(PasswordResetToken.class, tokenId);
            if (token != null) {
                Member member = token.getMember();
                entityManager.remove(token);
                entityManager.flush();
                entityManager.remove(member);
            }
            return null;
        });
    }

    @Test
    void 동일한_토큰을_동시에_사용하면_하나의_요청만_성공한다() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> firstRequest =
                    executor.submit(useToken(barrier, FIRST_PASSWORD));
            Future<Boolean> secondRequest =
                    executor.submit(useToken(barrier, SECOND_PASSWORD));

            List<Boolean> results = List.of(
                    firstRequest.get(10, TimeUnit.SECONDS),
                    secondRequest.get(10, TimeUnit.SECONDS)
            );

            assertThat(results).containsExactlyInAnyOrder(true, false);

            executeInTransaction(entityManager -> {
                PasswordResetToken token =
                        entityManager.find(PasswordResetToken.class, tokenId);

                assertThat(token.getUsedAt()).isNotNull();
                assertThat(token.getVersion()).isEqualTo(1);
                assertThat(token.getMember().getPassword())
                        .isIn(FIRST_PASSWORD, SECOND_PASSWORD);
                return null;
            });
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Boolean> useToken(
            CyclicBarrier barrier,
            String encodedPassword
    ) {
        return () -> {
            EntityManager entityManager = entityManagerFactory.createEntityManager();
            EntityTransaction transaction = entityManager.getTransaction();

            try {
                transaction.begin();
                PasswordResetToken token =
                        entityManager.find(PasswordResetToken.class, tokenId);

                // 두 트랜잭션이 같은 version을 읽은 뒤 동시에 변경하도록 조회 시점을 맞춘다.
                barrier.await(5, TimeUnit.SECONDS);

                token.markUsed(LocalDateTime.now());
                token.getMember().changePassword(encodedPassword);
                entityManager.flush();
                transaction.commit();
                return true;
            } catch (OptimisticLockException exception) {
                rollback(transaction);
                return false;
            } catch (Exception exception) {
                rollback(transaction);
                throw exception;
            } finally {
                entityManager.close();
            }
        };
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
}
