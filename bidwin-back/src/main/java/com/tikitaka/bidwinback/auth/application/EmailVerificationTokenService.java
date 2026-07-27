package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.auth.domain.entity.EmailVerificationToken;
import com.tikitaka.bidwinback.auth.domain.repository.EmailVerificationTokenRepository;
import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmailVerificationTokenService {

    private static final Duration TOKEN_VALIDITY = Duration.ofHours(24);

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final EmailVerificationTokenGenerator tokenGenerator;
    private final EmailVerificationTokenHasher tokenHasher;

    @Transactional
    public String issue(Member member) {
        String rawToken = tokenGenerator.generate();
        String tokenHash = tokenHasher.hash(rawToken);
        LocalDateTime issuedAt = LocalDateTime.now();

        emailVerificationTokenRepository.revokeAllActiveByMemberId(member.getId(), issuedAt);
        emailVerificationTokenRepository.save(
                EmailVerificationToken.issue(
                        member,
                        tokenHash,
                        issuedAt.plus(TOKEN_VALIDITY)
                )
        );

        return rawToken;
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
