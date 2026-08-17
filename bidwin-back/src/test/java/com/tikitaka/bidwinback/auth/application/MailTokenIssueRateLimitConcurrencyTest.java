package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.auth.application.emailverification.EmailVerificationTokenService;
import com.tikitaka.bidwinback.auth.application.passwordreset.PasswordResetTokenService;
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

import java.util.ArrayList;
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

@SpringBootTest(properties = {
        "app.mail.rate-limit.cooldown=0s",
        "app.mail.rate-limit.window=15m",
        "app.mail.rate-limit.max-count=5"
})
class MailTokenIssueRateLimitConcurrencyTest {

    private static final int REQUEST_COUNT = 10;
    private static final int MAX_ISSUE_COUNT = 5;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EmailVerificationTokenService emailVerificationTokenService;

    @Autowired
    private PasswordResetTokenService passwordResetTokenService;

    private Member pendingMember;
    private Member activeMember;

    @BeforeEach
    void setUp() {
        String identifier = UUID.randomUUID().toString().substring(0, 8);
        pendingMember = createMember(identifier + "-p", MemberStatus.PENDING);
        activeMember = createMember(identifier + "-a", MemberStatus.ACTIVE);
    }

    @AfterEach
    void tearDown() {
        deleteMemberAndTokens(pendingMember.getId());
        deleteMemberAndTokens(activeMember.getId());
    }

    @Test
    void 이메일_인증_토큰_동시_발급도_최대_횟수를_초과하지_않는다() throws Exception {
        long issuedCount = requestConcurrently(
                pendingMember,
                member -> emailVerificationTokenService.issue(member).isIssued()
        );

        assertThat(issuedCount).isEqualTo(MAX_ISSUE_COUNT);
        assertThat(countEmailVerificationTokens(pendingMember.getId()))
                .isEqualTo(MAX_ISSUE_COUNT);
    }

    @Test
    void 비밀번호_재설정_토큰_동시_발급도_최대_횟수를_초과하지_않는다() throws Exception {
        long issuedCount = requestConcurrently(
                activeMember,
                member -> passwordResetTokenService.issue(member).isPresent()
        );

        assertThat(issuedCount).isEqualTo(MAX_ISSUE_COUNT);
        assertThat(countPasswordResetTokens(activeMember.getId()))
                .isEqualTo(MAX_ISSUE_COUNT);
    }

    private long requestConcurrently(
            Member member,
            Function<Member, Boolean> issueToken
    ) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(REQUEST_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(REQUEST_COUNT);

        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int index = 0; index < REQUEST_COUNT; index++) {
                Callable<Boolean> request = () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return issueToken.apply(member);
                };
                futures.add(executor.submit(request));
            }

            long issuedCount = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(20, TimeUnit.SECONDS)) {
                    issuedCount++;
                }
            }
            return issuedCount;
        } finally {
            executor.shutdownNow();
        }
    }

    private Member createMember(String identifier, MemberStatus status) {
        return executeInTransaction(entityManager -> {
            Member member = Member.builder()
                    .email("rate-limit-%s@example.com".formatted(identifier))
                    .password("encoded-password")
                    .name("횟수제한테스트")
                    .phoneNumber("01012345678")
                    .nickname(identifier)
                    .status(status)
                    .build();
            entityManager.persist(member);
            entityManager.flush();
            return member;
        });
    }

    private long countEmailVerificationTokens(Long memberId) {
        return executeInTransaction(entityManager -> entityManager.createQuery(
                        "select count(token) from EmailVerificationToken token "
                                + "where token.member.id = :memberId",
                        Long.class
                )
                .setParameter("memberId", memberId)
                .getSingleResult());
    }

    private long countPasswordResetTokens(Long memberId) {
        return executeInTransaction(entityManager -> entityManager.createQuery(
                        "select count(token) from PasswordResetToken token "
                                + "where token.member.id = :memberId",
                        Long.class
                )
                .setParameter("memberId", memberId)
                .getSingleResult());
    }

    private void deleteMemberAndTokens(Long memberId) {
        executeInTransaction(entityManager -> {
            entityManager.createQuery(
                            "delete from EmailVerificationToken token where token.member.id = :memberId"
                    )
                    .setParameter("memberId", memberId)
                    .executeUpdate();
            entityManager.createQuery(
                            "delete from PasswordResetToken token where token.member.id = :memberId"
                    )
                    .setParameter("memberId", memberId)
                    .executeUpdate();

            Member member = entityManager.find(Member.class, memberId);
            if (member != null) {
                entityManager.remove(member);
            }
            return null;
        });
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
}
