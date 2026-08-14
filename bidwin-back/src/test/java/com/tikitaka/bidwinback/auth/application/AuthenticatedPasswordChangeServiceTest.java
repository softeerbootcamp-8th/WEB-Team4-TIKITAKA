package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.auth.domain.repository.PasswordResetTokenRepository;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.AuthMemberFixture;
import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticatedPasswordChangeServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-06T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordHasher passwordHasher;

    private AuthenticatedPasswordChangeService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticatedPasswordChangeService(
                memberRepository,
                passwordResetTokenRepository,
                passwordHasher,
                CLOCK
        );
    }

    @Test
    void 비밀번호를_변경하고_현재_세션용_새_인증_정보를_반환한다() {
        Instant loggedInAt = Instant.parse("2026-08-06T10:00:00Z");
        AuthMember currentAuth = AuthMemberFixture.of(1L, 3L, loggedInAt);
        Member member = org.mockito.Mockito.mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(member.getPassword()).thenReturn("encoded-current");
        when(member.getAuthVersion()).thenReturn(4L);
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(passwordHasher.matches("current!", "encoded-current")).thenReturn(true);
        when(passwordHasher.hash("new-password!")).thenReturn("encoded-new");

        AuthMember result = service.change(
                currentAuth,
                "current!",
                "new-password!",
                "new-password!"
        );

        assertThat(result).isEqualTo(new AuthMember(1L, 4L, loggedInAt));
        verify(member).changePassword("encoded-new");
        verify(passwordResetTokenRepository).revokeAllActiveByMemberId(
                1L,
                LocalDateTime.ofInstant(NOW, ZoneId.systemDefault())
        );
    }

    @Test
    void 새_비밀번호_확인이_다르면_회원도_조회하지_않는다() {
        AuthMember currentAuth = AuthMemberFixture.of(1L);

        assertThatExceptionOfType(AuthException.class)
                .isThrownBy(() -> service.change(
                        currentAuth,
                        "current!",
                        "new-password!",
                        "different-password!"
                ))
                .extracting(AuthException::getErrorCode)
                .isEqualTo(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        verifyNoInteractions(memberRepository, passwordHasher);
    }

    @Test
    void 현재_비밀번호가_다르면_변경하지_않는다() {
        AuthMember currentAuth = AuthMemberFixture.of(1L);
        Member member = org.mockito.Mockito.mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(member.getPassword()).thenReturn("encoded-current");
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(passwordHasher.matches("wrong!", "encoded-current")).thenReturn(false);

        assertThatExceptionOfType(AuthException.class)
                .isThrownBy(() -> service.change(
                        currentAuth,
                        "wrong!",
                        "new-password!",
                        "new-password!"
                ))
                .extracting(AuthException::getErrorCode)
                .isEqualTo(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        verify(passwordHasher, never()).hash(org.mockito.ArgumentMatchers.anyString());
        verifyNoInteractions(passwordResetTokenRepository);
    }

    @Test
    void 현재와_같은_새_비밀번호는_거절한다() {
        AuthMember currentAuth = AuthMemberFixture.of(1L);
        Member member = org.mockito.Mockito.mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(member.getPassword()).thenReturn("encoded-current");
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(passwordHasher.matches("same-password!", "encoded-current"))
                .thenReturn(true);

        assertThatExceptionOfType(AuthException.class)
                .isThrownBy(() -> service.change(
                        currentAuth,
                        "same-password!",
                        "same-password!",
                        "same-password!"
                ))
                .extracting(AuthException::getErrorCode)
                .isEqualTo(ErrorCode.NEW_PASSWORD_SAME_AS_CURRENT);
        verify(passwordHasher, never()).hash(org.mockito.ArgumentMatchers.anyString());
    }
}
