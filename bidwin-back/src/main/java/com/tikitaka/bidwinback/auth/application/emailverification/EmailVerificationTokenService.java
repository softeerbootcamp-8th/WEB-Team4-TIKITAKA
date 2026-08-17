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
import java.util.ArrayList;
import java.util.List;
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
    public EmailVerificationIssueResult issue(Member member) {
        Long memberId = member.getId();
        Optional<Member> lockedMember = memberRepository.findByIdForUpdate(memberId);
        if (lockedMember.isEmpty()
                || lockedMember.get().getStatus() != MemberStatus.PENDING) {
            // 회원 상태가 응답으로 드러나지 않도록 새로 발급했을 때와 같은 대기 시간을 돌려준다.
            return EmailVerificationIssueResult.notIssued(mailRateLimitProperties.cooldown());
        }

        LocalDateTime issuedAt = LocalDateTime.now();
        List<LocalDateTime> recentIssuedAt = emailVerificationTokenRepository.findIssuedAtSince(
                memberId,
                issuedAt.minus(mailRateLimitProperties.window())
        );

        Duration retryAfter = retryAfter(recentIssuedAt, issuedAt);
        if (!retryAfter.isZero()) {
            return EmailVerificationIssueResult.notIssued(retryAfter);
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

        return EmailVerificationIssueResult.issued(
                rawToken,
                retryAfterIncluding(issuedAt, recentIssuedAt)
        );
    }

    // 방금 발급한 건까지 포함해 다음 재발급까지 남은 시간을 다시 계산한다.
    private Duration retryAfterIncluding(
            LocalDateTime issuedAt,
            List<LocalDateTime> recentIssuedAt
    ) {
        List<LocalDateTime> issuedAtDesc = new ArrayList<>(recentIssuedAt.size() + 1);
        issuedAtDesc.add(issuedAt);
        issuedAtDesc.addAll(recentIssuedAt);
        return retryAfter(issuedAtDesc, issuedAt);
    }

    /*
     * 다음 발급이 가능해질 때까지 남은 시간. 지금 발급할 수 있으면 0이다.
     * 쿨다운과 창 단위 상한 중 더 늦게 풀리는 쪽을 기준으로 삼는다.
     */
    private Duration retryAfter(List<LocalDateTime> issuedAtDesc, LocalDateTime now) {
        Duration retryAfter = Duration.ZERO;

        Duration cooldown = mailRateLimitProperties.cooldown();
        if (!cooldown.isZero() && !issuedAtDesc.isEmpty()) {
            LocalDateTime latestIssuedAt = issuedAtDesc.getFirst();
            retryAfter = later(retryAfter, remainingUntil(latestIssuedAt.plus(cooldown), now));
        }

        int maxCount = mailRateLimitProperties.maxCount();
        if (issuedAtDesc.size() >= maxCount) {
            // 상한을 채운 발급 중 가장 오래된 건이 창을 벗어나야 다시 발급할 수 있다.
            LocalDateTime blockingIssuedAt = issuedAtDesc.get(maxCount - 1);
            retryAfter = later(
                    retryAfter,
                    remainingUntil(blockingIssuedAt.plus(mailRateLimitProperties.window()), now)
            );
        }

        return retryAfter;
    }

    private Duration remainingUntil(LocalDateTime availableAt, LocalDateTime now) {
        Duration remaining = Duration.between(now, availableAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private Duration later(Duration left, Duration right) {
        return left.compareTo(right) >= 0 ? left : right;
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
