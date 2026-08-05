package com.tikitaka.bidwinback.auth.application.emailverification;

import com.tikitaka.bidwinback.auth.application.TokenGenerator;
import com.tikitaka.bidwinback.auth.application.TokenHasher;
import com.tikitaka.bidwinback.auth.domain.entity.EmailVerificationToken;
import com.tikitaka.bidwinback.auth.domain.repository.EmailVerificationTokenRepository;
import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.config.MailRateLimitProperties;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmailVerificationTokenService {

    private static final Duration TOKEN_VALIDITY = Duration.ofMinutes(15);

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final MemberRepository memberRepository;
    private final TokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher;
    private final MailRateLimitProperties mailRateLimitProperties;

    @Transactional
    public Optional<String> issue(Member member) {
        Long memberId = member.getId();
        Optional<Member> lockedMember = memberRepository.findByIdForUpdate(memberId);
        if (lockedMember.isEmpty()
                || lockedMember.get().getStatus() != MemberStatus.PENDING) {
            return Optional.empty();
        }

        LocalDateTime issuedAt = LocalDateTime.now();
        if (isRateLimited(memberId, issuedAt)) {
            return Optional.empty();
        }

        String rawToken = tokenGenerator.generate();
        String tokenHash = tokenHasher.hash(rawToken);

        emailVerificationTokenRepository.revokeAllActiveByMemberId(memberId, issuedAt);
        emailVerificationTokenRepository.save(
                EmailVerificationToken.issue(
                        lockedMember.get(),
                        tokenHash,
                        issuedAt.plus(TOKEN_VALIDITY)
                )
        );

        return Optional.of(rawToken);
    }

    private boolean isRateLimited(Long memberId, LocalDateTime issuedAt) {
        Duration cooldown = mailRateLimitProperties.cooldown();
        // 쿨다운 시간 내 발급 이력이 있으면 재발급을 제한한다.
        if (!cooldown.isZero()
                && emailVerificationTokenRepository.countIssuedSince(
                        memberId,
                        issuedAt.minus(cooldown)
                ) > 0) {
            return true;
        }

        // 제한 시간 내 최대 발급 횟수에 도달했는지 확인한다.
        return emailVerificationTokenRepository.countIssuedSince(
                memberId,
                issuedAt.minus(mailRateLimitProperties.window())
        ) >= mailRateLimitProperties.maxCount();
    }

    @Transactional
    public void verify(String rawToken) {
        // 원본 토큰은 저장하지 않으므로 요청 토큰도 동일하게 해시한 뒤 조회한다.
        String tokenHash = tokenHasher.hash(rawToken);

        LocalDateTime now = LocalDateTime.now();
        EmailVerificationToken token = emailVerificationTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN));

        validateUsable(token, now);

        try {
            token.markUsed(now);
            token.getMember().activate();
            emailVerificationTokenRepository.flush();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new AuthException(ErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN);
        }
    }

    private void validateUsable(EmailVerificationToken token, LocalDateTime now) {
        // 이미 사용하거나 재전송으로 폐기된 토큰은 동일한 잘못된 토큰으로 처리한다.
        if (token.getUsedAt() != null || token.getRevokedAt() != null) {
            throw new AuthException(ErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN);
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw new AuthException(ErrorCode.EXPIRED_EMAIL_VERIFICATION_TOKEN);
        }
    }
}
