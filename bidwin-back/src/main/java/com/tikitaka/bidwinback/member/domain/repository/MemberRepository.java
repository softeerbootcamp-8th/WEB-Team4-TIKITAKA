package com.tikitaka.bidwinback.member.domain.repository;

import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select member from Member member where member.id = :memberId")
    Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);

    boolean existsByIdAndStatusAndAuthVersion(
            Long id,
            MemberStatus status,
            long authVersion
    );

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    // 잔액 확인과 요청 금액 잠금을 한 UPDATE로 처리한다.
    @Modifying
    @Query(value = """
            UPDATE member
            SET total_point = total_point - :amount,
                locked_point = locked_point + :amount
            WHERE id = :memberId
              AND status = 'ACTIVE'
              AND total_point >= :amount
            """, nativeQuery = true)
    int movePointToLockedIfEnough(
            @Param("memberId") Long memberId,
            @Param("amount") long amount
    );

    // 보증금 반환: 잠금액을 사용 가능 잔액으로 원자적으로 되돌린다.
    // 회원 상태와 무관하게 정산돼야 하므로 status 조건 없이 잠금 잔액만 확인한다.
    @Modifying
    @Query(value = """
            UPDATE member
            SET total_point = total_point + :amount,
                locked_point = locked_point - :amount
            WHERE id = :memberId
              AND locked_point >= :amount
            """, nativeQuery = true)
    int refundLockedPoint(
            @Param("memberId") Long memberId,
            @Param("amount") long amount
    );

    // 보증금 몰수: 잠금액만 원자적으로 차감하고 사용 가능 잔액으로 되돌리지 않는다.
    @Modifying
    @Query(value = """
            UPDATE member
            SET locked_point = locked_point - :amount
            WHERE id = :memberId
              AND locked_point >= :amount
            """, nativeQuery = true)
    int forfeitLockedPoint(
            @Param("memberId") Long memberId,
            @Param("amount") long amount
    );

    // 판매자 정산 지급: 사용 가능 잔액을 원자적으로 늘린다.
    @Modifying
    @Query(value = """
            UPDATE member
            SET total_point = total_point + :amount
            WHERE id = :memberId
            """, nativeQuery = true)
    int creditPoint(
            @Param("memberId") Long memberId,
            @Param("amount") long amount
    );
}
