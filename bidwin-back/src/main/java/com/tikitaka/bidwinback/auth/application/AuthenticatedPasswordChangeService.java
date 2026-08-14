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

    /**
     * 세션(Redis) 갱신은 이 메서드가 반환된(=DB 커밋이 끝난) 이후 컨트롤러가 담당한다.
     * HttpSession은 웹 계층 개념이라 이 서비스가 알 필요가 없고, 커밋 전에 세션을 먼저
     * 갱신하면 그 이후 커밋 자체가 실패할 때 반대 방향의 불일치(세션은 새 버전인데 DB는
     * 롤백된 옛 버전)가 생긴다. 세션 갱신이 실패해도 비밀번호 변경 자체는 이미 유효하므로,
     * 그 경우 이 세션만 예전 authVersion을 들고 있다가 다음 인증 요청에서 자연스럽게
     * 재로그인이 유도되게 한다(다른 기기 세션과 동일한 방식).
     */
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
