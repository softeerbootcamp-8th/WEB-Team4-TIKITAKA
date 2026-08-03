package com.tikitaka.bidwinback.member.domain.entity;

import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Member의 @DynamicUpdate가 authVersion 보호에 실제로 기여하는지 확인한다.
 * 애노테이션이 사라지면 전체 컬럼 UPDATE가 다른 트랜잭션의 authVersion 증가를 되돌린다.
 */
@SpringBootTest
class MemberAuthVersionPersistenceTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Long memberId;

    @BeforeEach
    void setUp() {
        String identifier = UUID.randomUUID().toString().substring(0, 8);
        memberId = executeInTransaction(entityManager -> {
            Member member = Member.builder()
                    .email("auth-version-%s@example.com".formatted(identifier))
                    .password("encoded-old-password")
                    .name("인증버전테스트")
                    .phoneNumber("01012345678")
                    .nickname(identifier)
                    .build();
            entityManager.persist(member);
            entityManager.flush();
            return member.getId();
        });
    }

    @AfterEach
    void tearDown() {
        if (memberId == null) {
            return;
        }

        executeInTransaction(entityManager -> {
            Member member = entityManager.find(Member.class, memberId);
            if (member != null) {
                entityManager.remove(member);
            }
            return null;
        });
    }

    @Test
    void 다른_컬럼을_변경한_트랜잭션이_인증_버전_증가를_덮어쓰지_않는다() {
        // given
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();
        transaction.begin();

        // authVersion 0을 읽은 뒤 상태 컬럼만 변경한다.
        Member member = entityManager.find(Member.class, memberId);
        member.activate();

        // when
        // 아직 flush하지 않은 사이에 다른 트랜잭션이 비밀번호를 변경해 authVersion을 올린다.
        executeInTransaction(other -> {
            other.find(Member.class, memberId).changePassword("encoded-new-password");
            return null;
        });

        transaction.commit();
        entityManager.close();

        // then
        executeInTransaction(reader -> {
            Member persisted = reader.find(Member.class, memberId);
            assertThat(persisted.getAuthVersion()).isEqualTo(1L);
            assertThat(persisted.getStatus()).isEqualTo(MemberStatus.ACTIVE);
            return null;
        });
    }

    private <T> T executeInTransaction(Function<EntityManager, T> work) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();

        try {
            transaction.begin();
            T result = work.apply(entityManager);
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
