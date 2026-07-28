package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.auth.application.emailverification.EmailVerificationTokenService;
import com.tikitaka.bidwinback.auth.application.passwordreset.PasswordResetTokenService;
import com.tikitaka.bidwinback.auth.domain.enums.MailPurpose;
import com.tikitaka.bidwinback.auth.presentation.dto.response.AvailabilityResponse;
import com.tikitaka.bidwinback.auth.presentation.dto.request.EmailAvailabilityRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.EmailVerificationRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.EmailVerificationSendRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.LoginRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.NicknameAvailabilityRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.PasswordChangeRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.PasswordResetRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.request.SignUpRequest;
import com.tikitaka.bidwinback.auth.presentation.dto.response.SignUpResponse;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.exception.AuthException;
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

import static org.assertj.core.api.Assertions.assertThat;
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

import java.time.Instant;
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
    private EmailVerificationTokenService emailVerificationTokenService;

    @Mock
    private TokenMailSender tokenMailSender;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                new MemberService(memberRepository),
                passwordHasher,
                passwordResetTokenService,
                emailVerificationTokenService,
                tokenMailSender
        );
    }

    @Test
    void 사용_가능한_이메일을_확인한다() {
        EmailAvailabilityRequest request =
                new EmailAvailabilityRequest("member@example.com");
        when(memberRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        AvailabilityResponse response = authService.checkEmailAvailability(request);

        assertTrue(response.available());
        verify(memberRepository).findByEmail(request.email());
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
        Member existingMember = Member.builder()
                .email(request.email())
                .password("encoded-password")
                .name("기존회원")
                .phoneNumber("01099998888")
                .nickname("기존닉네임")
                .status(MemberStatus.ACTIVE)
                .build();
        when(memberRepository.findByEmail(request.email())).thenReturn(Optional.of(existingMember));

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
        when(memberRepository.findByEmail(request.email())).thenReturn(Optional.empty());
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

    @Test
    void 회원가입은_이메일_인증_토큰_발급이나_메일_전송을_수행하지_않는다() {
        SignUpRequest request = createSignUpRequest();
        when(passwordHasher.hash(request.password())).thenReturn("encoded-password");
        when(memberRepository.saveAndFlush(any(Member.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authService.signup(request);

        verifyNoInteractions(emailVerificationTokenService, tokenMailSender);
    }

    @Test
    void 이메일_인증_메일_발송_요청_시_토큰을_발급하고_메일을_전송한다() {
        Member member = Member.builder()
                .email("member@example.com")
                .password("encoded-password")
                .name("홍길동")
                .phoneNumber("01012345678")
                .nickname("티키타카")
                .build();
        when(memberRepository.findByEmail(member.getEmail()))
                .thenReturn(Optional.of(member));
        when(emailVerificationTokenService.issue(member))
                .thenReturn("raw-email-verification-token");

        authService.sendVerificationEmail(new EmailVerificationSendRequest(member.getEmail()));

        verify(emailVerificationTokenService).issue(member);
        verify(tokenMailSender)
                .send(MailPurpose.EMAIL_VERIFICATION, member.getEmail(), "raw-email-verification-token");
    }

    @Test
    void 존재하지_않는_회원에게_인증_메일_발송을_요청하면_예외가_발생한다() {
        when(memberRepository.findByEmail("unknown@example.com"))
                .thenReturn(Optional.empty());

        assertThatExceptionOfType(AuthException.class)
                .isThrownBy(() -> authService.sendVerificationEmail(
                        new EmailVerificationSendRequest("unknown@example.com")))
                .extracting(AuthException::getErrorCode)
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        verifyNoInteractions(tokenMailSender);
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
    void 로그인하면_인증_정보에_로그인_시각을_기록한다() {
        // given
        LoginRequest request = new LoginRequest("member@example.com", "password!");
        Member member = mock(Member.class);
        when(memberRepository.findByEmail(request.email())).thenReturn(Optional.of(member));
        when(member.getPassword()).thenReturn("encoded-password");
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(member.getId()).thenReturn(1L);
        when(passwordHasher.matches(request.password(), "encoded-password")).thenReturn(true);
        Instant beforeLogin = Instant.now();

        // when
        AuthMember authMember = authService.login(request);

        // then
        assertThat(authMember.loggedInAt()).isBetween(beforeLogin, Instant.now());
    }

    @Test
    void 로그인하면_현재_회원의_인증_버전을_인증_정보에_기록한다() {
        // given
        LoginRequest request = new LoginRequest("member@example.com", "password!");
        Member member = mock(Member.class);
        when(memberRepository.findByEmail(request.email())).thenReturn(Optional.of(member));
        when(member.getPassword()).thenReturn("encoded-password");
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(member.getId()).thenReturn(1L);
        when(member.getAuthVersion()).thenReturn(3L);
        when(passwordHasher.matches(request.password(), "encoded-password")).thenReturn(true);

        // when
        AuthMember authMember = authService.login(request);

        // then
        assertThat(authMember.authVersion()).isEqualTo(3L);
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
        verify(tokenMailSender).send(MailPurpose.PASSWORD_RESET, request.email(), "raw-reset-token");
    }

    @Test
    void 존재하지_않는_회원의_재설정_요청은_토큰과_메일을_생성하지_않는다() {
        PasswordResetRequest request = new PasswordResetRequest("unknown@example.com");
        when(memberRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        authService.requestPasswordReset(request);

        verifyNoInteractions(passwordResetTokenService, tokenMailSender);
    }

    @Test
    void 비활성_회원의_재설정_요청은_토큰과_메일을_생성하지_않는다() {
        PasswordResetRequest request = new PasswordResetRequest("member@example.com");
        Member member = mock(Member.class);
        when(memberRepository.findByEmail(request.email())).thenReturn(Optional.of(member));
        when(member.getStatus()).thenReturn(MemberStatus.BANNED);

        authService.requestPasswordReset(request);

        verifyNoInteractions(passwordResetTokenService, tokenMailSender);
    }

    @Test
    void 새_비밀번호와_확인_비밀번호가_일치하면_비밀번호를_변경한다() {
        PasswordChangeRequest request = new PasswordChangeRequest(
                "raw-reset-token",
                "new-password!",
                "new-password!"
        );

        authService.resetPassword(request);

        verify(passwordResetTokenService)
                .resetPassword(request.token(), request.newPassword());
    }

    @Test
    void 새_비밀번호와_확인_비밀번호가_다르면_변경하지_않는다() {
        PasswordChangeRequest request = new PasswordChangeRequest(
                "raw-reset-token",
                "new-password!",
                "different-password!"
        );

        AuthException exception = assertThrows(
                AuthException.class,
                () -> authService.resetPassword(request)
        );

        assertEquals(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH, exception.getErrorCode());
        verifyNoInteractions(passwordResetTokenService);
    }

    @Test
    void 이메일_인증을_요청하면_토큰_검증을_위임한다() {
        EmailVerificationRequest request = new EmailVerificationRequest("raw-email-verification-token");

        authService.verifyEmail(request);

        verify(emailVerificationTokenService).verify(request.token());
    }
}
