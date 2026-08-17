package com.tikitaka.bidwinback.auth.domain.repository;

import com.tikitaka.bidwinback.auth.domain.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    @Query("""
            select token
            from EmailVerificationToken token
            join fetch token.member
            where token.tokenHash = :tokenHash
            """)
    Optional<EmailVerificationToken> findByTokenHash(
            @Param("tokenHash") String tokenHash
    );

    // 남은 재발급 대기 시간까지 계산해야 해서 횟수 대신 발급 시각을 최신순으로 가져온다.
    @Query("""
            select token.createdAt
            from EmailVerificationToken token
            where token.member.id = :memberId
              and token.createdAt >= :issuedSince
            order by token.createdAt desc
            """)
    List<LocalDateTime> findIssuedAtSince(
            @Param("memberId") Long memberId,
            @Param("issuedSince") LocalDateTime issuedSince
    );

    // 재전송 시 이전에 발급된 미사용·미만료 토큰을 더 이상 사용할 수 없도록 폐기한다.
    @Modifying
    @Query("""
            update EmailVerificationToken token
            set token.revokedAt = :revokedAt,
                token.version = token.version + 1
            where token.member.id = :memberId
              and token.usedAt is null
              and token.revokedAt is null
              and token.expiresAt > :revokedAt
            """)
    int revokeAllActiveByMemberId(
            @Param("memberId") Long memberId,
            @Param("revokedAt") LocalDateTime revokedAt
    );
}
