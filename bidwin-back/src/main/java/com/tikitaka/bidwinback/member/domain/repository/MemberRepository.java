package com.tikitaka.bidwinback.member.domain.repository;

import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    boolean existsByIdAndStatusAndAuthVersion(
            Long id,
            MemberStatus status,
            long authVersion
    );

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    // 잔액 확인과 전액 잠금을 한 UPDATE로 처리한다.
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
}
