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
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class AuthenticatedPasswordChangeService {

    private final MemberRepository memberRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    /**
     * 세션 갱신(onPasswordChanged)을 같은 트랜잭션 안에서 실행한다. 세션 갱신이 실패하면
     * 비밀번호는 이미 바뀌었는데 세션은 예전 자격을 들고 있는 부분 성공 상태가 되므로,
     * 실패 시 이 메서드 전체(비밀번호 변경 포함)를 함께 롤백해 원자성을 지킨다.
     * 대가로 Redis 응답을 기다리는 동안 이 회원 행의 비관적 락을 더 오래 붙잡게 되는데,
     * 비밀번호 변경은 빈도가 낮은 작업이라 이 트레이드오프를 감수한다.
     */
    @Transactional
    public AuthMember change(
            AuthMember currentAuth,
            String currentPassword,
            String newPassword,
            String newPasswordConfirm,
            Consumer<AuthMember> onPasswordChanged
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
        AuthMember refreshedAuth = AuthMember.from(member, currentAuth.loggedInAt());
        onPasswordChanged.accept(refreshedAuth);
        return refreshedAuth;
    }
}
