package com.tikitaka.bidwinback.auth.domain.entity;

import com.tikitaka.bidwinback.global.common.entity.BaseTimeEntity;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import jakarta.persistence.*;

import java.time.LocalDateTime;

public class EmailVerificationToken extends BaseTimeEntity {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;      // 원본 토큰이 아니라 해시로 저장 (PasswordResetToken이랑 같은 이유: 유출돼도 안전)

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;   // 인증 완료되면 언제 썼는지 기록 (재사용 방지)
}