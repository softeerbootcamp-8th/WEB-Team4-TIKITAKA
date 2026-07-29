package com.tikitaka.bidwinback.auth.application.passwordreset;

import com.tikitaka.bidwinback.auth.application.PasswordHasher;
import com.tikitaka.bidwinback.auth.application.TokenGenerator;
import com.tikitaka.bidwinback.auth.application.TokenHasher;
import com.tikitaka.bidwinback.auth.domain.entity.PasswordResetToken;
import com.tikitaka.bidwinback.auth.domain.repository.PasswordResetTokenRepository;
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
public class PasswordResetTokenService {

    private static final Duration TOKEN_VALIDITY = Duration.ofMinutes(15);

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final TokenGenerator tokenGenerator;
    private final TokenHasher tokenHasher;
    private final PasswordHasher passwordHasher;

    @Transactional
    public String issue(Member member) {
        String rawToken = tokenGenerator.generate();
        String tokenHash = tokenHasher.hash(rawToken);
        LocalDateTime issuedAt = LocalDateTime.now();

        passwordResetTokenRepository.revokeAllActiveByMemberId(member.getId(), issuedAt);
        passwordResetTokenRepository.save(
                PasswordResetToken.issue(
                        member,
                        tokenHash,
                        issuedAt.plus(TOKEN_VALIDITY)
                )
        );

        return rawToken;
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        // 원본 토큰은 저장하지 않으므로 요청 토큰도 동일하게 해시한 뒤 조회한다.
        String tokenHash = tokenHasher.hash(rawToken);

        LocalDateTime now = LocalDateTime.now();
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN));

        validateUsable(token, now);

        // 비용이 큰 비밀번호 해싱은 유효한 토큰임을 확인한 뒤에만 수행한다.
        String encodedPassword = passwordHasher.hash(newPassword);

        try {
            token.markUsed(now);
            // 비밀번호 변경과 토큰 사용 처리는 같은 트랜잭션에서 함께 반영한다.
            token.getMember().changePassword(encodedPassword);
            passwordResetTokenRepository.flush();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new AuthException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
        }
    }

    private void validateUsable(PasswordResetToken token, LocalDateTime now) {
        // 이미 사용하거나 재발급으로 폐기된 토큰은 동일한 잘못된 토큰으로 처리한다.
        if (token.getUsedAt() != null || token.getRevokedAt() != null) {
            throw new AuthException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw new AuthException(ErrorCode.EXPIRED_PASSWORD_RESET_TOKEN);
        }
    }
}
