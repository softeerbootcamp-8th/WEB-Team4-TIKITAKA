package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.auth.domain.entity.EmailVerificationToken;
import com.tikitaka.bidwinback.auth.domain.repository.EmailVerificationTokenRepository;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import lombok.RequiredArgsConstructor;
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
}
