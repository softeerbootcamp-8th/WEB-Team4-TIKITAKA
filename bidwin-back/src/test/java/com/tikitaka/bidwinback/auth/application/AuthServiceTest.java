package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.dto.AvailabilityResponse;
import com.tikitaka.bidwinback.dto.EmailAvailabilityRequest;
import com.tikitaka.bidwinback.dto.NicknameAvailabilityRequest;
import com.tikitaka.bidwinback.dto.LoginRequest;
import com.tikitaka.bidwinback.dto.PasswordResetRequest;
import com.tikitaka.bidwinback.dto.SignUpRequest;
import com.tikitaka.bidwinback.dto.SignUpResponse;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.member.application.MemberService;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import com.tikitaka.bidwinback.member.domain.exception.MemberException;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private PasswordResetTokenService passwordResetTokenService;

    @Mock
    private PasswordResetMailSender passwordResetMailSender;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                new MemberService(memberRepository),
                passwordHasher,
                passwordResetTokenService,
                passwordResetMailSender
        );
    }

    @Test
    void 사용_가능한_이메일을_확인한다() {
        EmailAvailabilityRequest request =
                new EmailAvailabilityRequest("member@example.com");
        when(memberRepository.existsByEmail(request.email())).thenReturn(false);

        AvailabilityResponse response = authService.checkEmailAvailability(request);

        assertTrue(response.available());
        verify(memberRepository).existsByEmail(request.email());
    }

    @Test
    void 사용_가능한_닉네임을_확인한다() {
        NicknameAvailabilityRequest request = new NicknameAvailabilityRequest("티키타카");
        when(memberRepository.existsByNickname(request.nickname())).thenReturn(false);

        AvailabilityResponse response = authService.checkNicknameAvailability(request);

        assertTrue(response.available());
        verify(memberRepository).existsByNickname(request.nickname());
    }

    @Test
    void 회원가입_시_이메일이_중복되면_해싱과_저장을_하지_않는다() {
        SignUpRequest request = createSignUpRequest();
        when(memberRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatExceptionOfType(MemberException.class)
                .isThrownBy(() -> authService.signup(request))
                .extracting(MemberException::getErrorCode)
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        verify(passwordHasher, never()).hash(any(String.class));
        verify(memberRepository, never()).saveAndFlush(any(Member.class));
    }

    @Test
    void 회원가입_시_닉네임이_중복되면_해싱과_저장을_하지_않는다() {
        SignUpRequest request = createSignUpRequest();
        when(memberRepository.existsByEmail(request.email())).thenReturn(false);
        when(memberRepository.existsByNickname(request.nickname())).thenReturn(true);

        assertThatExceptionOfType(MemberException.class)
                .isThrownBy(() -> authService.signup(request))
                .extracting(MemberException::getErrorCode)
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
        verify(passwordHasher, never()).hash(any(String.class));
        verify(memberRepository, never()).saveAndFlush(any(Member.class));
    }

    @Test
    void 회원가입_시_비밀번호를_해싱하고_회원을_저장한다() {
        SignUpRequest request = createSignUpRequest();
        when(passwordHasher.hash(request.password())).thenReturn("encoded-password");
        when(memberRepository.saveAndFlush(any(Member.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SignUpResponse response = authService.signup(request);

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).saveAndFlush(captor.capture());
        Member savedMember = captor.getValue();
        assertAll(
                () -> assertEquals("encoded-password", savedMember.getPassword()),
                () -> assertEquals(request.email(), savedMember.getEmail()),
                () -> assertEquals(request.name(), savedMember.getName()),
                () -> assertEquals(request.phoneNumber(), savedMember.getPhoneNumber()),
                () -> assertEquals(request.nickname(), savedMember.getNickname()),
                () -> assertFalse(savedMember.getProfileObjectKey().isBlank()),
                () -> assertEquals(MemberStatus.PENDING, savedMember.getStatus()),
                () -> assertEquals(request.email(), response.email()),
                () -> assertEquals(request.nickname(), response.nickname())
        );
    }

    private SignUpRequest createSignUpRequest() {
        return new SignUpRequest(
                "member@example.com",
                "password!",
                "홍길동",
                "01012345678",
                "티키타카"
        );
    }

    @Test
    void 활성_회원의_이메일과_비밀번호가_일치하면_인증_회원을_반환한다() {
        // given
        LoginRequest request = new LoginRequest("member@example.com", "password!");
        Member member = mock(Member.class);
        when(memberRepository.findByEmail(request.email())).thenReturn(Optional.of(member));
        when(member.getPassword()).thenReturn("encoded-password");
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(member.getId()).thenReturn(1L);
        when(passwordHasher.matches(request.password(), "encoded-password")).thenReturn(true);

        // when
        AuthMember authMember = authService.login(request);

        // then
        assertEquals(1L, authMember.memberId());
    }

    @Test
    void 비밀번호가_일치하지_않으면_인증에_실패한다() {
        // given
        LoginRequest request = new LoginRequest("member@example.com", "wrong-password!");
        Member member = mock(Member.class);
        when(memberRepository.findByEmail(request.email())).thenReturn(Optional.of(member));
        when(member.getPassword()).thenReturn("encoded-password");

        // when
        AuthException exception = assertThrows(
                AuthException.class,
                () -> authService.login(request)
        );

        // then
        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
    }

    @Test
    void 존재하지_않는_이메일도_동일한_인증_실패로_처리한다() {
        // given
        LoginRequest request = new LoginRequest("unknown@example.com", "password!");
        when(memberRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        // when
        AuthException exception = assertThrows(
                AuthException.class,
                () -> authService.login(request)
        );

        // then
        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
    }

    @Test
    void 활성_상태가_아닌_회원은_로그인할_수_없다() {
        // given
        LoginRequest request = new LoginRequest("member@example.com", "password!");
        Member member = mock(Member.class);
        when(memberRepository.findByEmail(request.email())).thenReturn(Optional.of(member));
        when(member.getPassword()).thenReturn("encoded-password");
        when(member.getStatus()).thenReturn(MemberStatus.BANNED);
        when(passwordHasher.matches(request.password(), "encoded-password")).thenReturn(true);

        // when
        AuthException exception = assertThrows(
                AuthException.class,
                () -> authService.login(request)
        );

        // then
        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
    }

    @Test
    void 활성_회원이_비밀번호_재설정을_요청하면_토큰을_발급하고_메일을_전송한다() {
        PasswordResetRequest request = new PasswordResetRequest("member@example.com");
        Member member = mock(Member.class);
        when(memberRepository.findByEmail(request.email())).thenReturn(Optional.of(member));
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(member.getEmail()).thenReturn(request.email());
        when(passwordResetTokenService.issue(member)).thenReturn("raw-reset-token");

        authService.requestPasswordReset(request);

        verify(passwordResetTokenService).issue(member);
        verify(passwordResetMailSender).send(request.email(), "raw-reset-token");
    }

    @Test
    void 존재하지_않는_회원의_재설정_요청은_토큰과_메일을_생성하지_않는다() {
        PasswordResetRequest request = new PasswordResetRequest("unknown@example.com");
        when(memberRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        authService.requestPasswordReset(request);

        verifyNoInteractions(passwordResetTokenService, passwordResetMailSender);
    }

    @Test
    void 비활성_회원의_재설정_요청은_토큰과_메일을_생성하지_않는다() {
        PasswordResetRequest request = new PasswordResetRequest("member@example.com");
        Member member = mock(Member.class);
        when(memberRepository.findByEmail(request.email())).thenReturn(Optional.of(member));
        when(member.getStatus()).thenReturn(MemberStatus.BANNED);

        authService.requestPasswordReset(request);

        verifyNoInteractions(passwordResetTokenService, passwordResetMailSender);
    }
}
