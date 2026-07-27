package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.auth.domain.entity.PasswordResetToken;
import com.tikitaka.bidwinback.auth.domain.repository.PasswordResetTokenRepository;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PasswordResetTokenService {

    private static final Duration TOKEN_VALIDITY = Duration.ofMinutes(15);

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetTokenGenerator tokenGenerator;
    private final PasswordResetTokenHasher tokenHasher;

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
}
