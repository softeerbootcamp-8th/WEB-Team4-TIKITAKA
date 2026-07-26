package com.tikitaka.bidwinback.auth.domain.repository;

import com.tikitaka.bidwinback.auth.domain.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    // 재발급 시 이전에 발급된 미사용·미만료 토큰을 더 이상 사용할 수 없도록 폐기한다.
    @Modifying
    @Query("""
            update PasswordResetToken token
            set token.revokedAt = :revokedAt
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
