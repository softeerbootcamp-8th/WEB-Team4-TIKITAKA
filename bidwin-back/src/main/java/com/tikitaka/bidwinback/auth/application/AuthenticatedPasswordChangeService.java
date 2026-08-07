package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.auth.domain.repository.PasswordResetTokenRepository;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class AuthenticatedPasswordChangeService {

    private final MemberRepository memberRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    @Transactional
    public AuthMember change(
            AuthMember currentAuth,
            String currentPassword,
            String newPassword,
            String newPasswordConfirm
    ) {
        if (!newPassword.equals(newPasswordConfirm)) {
            throw new AuthException(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        }

        Member member = memberRepository.findByIdForUpdate(currentAuth.memberId())
                .filter(found -> found.getStatus() == MemberStatus.ACTIVE)
                .orElseThrow(() -> new AuthException(ErrorCode.UNAUTHENTICATED));

        if (!passwordHasher.matches(currentPassword, member.getPassword())) {
            throw new AuthException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        if (currentPassword.equals(newPassword)) {
            throw new AuthException(ErrorCode.NEW_PASSWORD_SAME_AS_CURRENT);
        }

        String encodedPassword = passwordHasher.hash(newPassword);
        member.changePassword(encodedPassword);
        passwordResetTokenRepository.revokeAllActiveByMemberId(
                member.getId(),
                LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault())
        );

        // 현재 세션은 새 인증 버전으로 갱신하고, 로그인 절대 만료 시각은 연장하지 않는다.
        return AuthMember.from(member, currentAuth.loggedInAt());
    }
}
