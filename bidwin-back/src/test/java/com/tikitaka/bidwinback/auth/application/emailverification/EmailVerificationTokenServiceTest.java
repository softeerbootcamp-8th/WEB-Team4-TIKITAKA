package com.tikitaka.bidwinback.auth.application.emailverification;

import com.tikitaka.bidwinback.auth.application.TokenGenerator;
import com.tikitaka.bidwinback.auth.application.TokenHasher;
import com.tikitaka.bidwinback.auth.domain.entity.EmailVerificationToken;
import com.tikitaka.bidwinback.auth.domain.repository.EmailVerificationTokenRepository;
import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.config.MailRateLimitProperties;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationTokenServiceTest {

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private TokenGenerator tokenGenerator;

    @Mock
    private TokenHasher tokenHasher;

    @Mock
    private Member member;

    private EmailVerificationTokenService emailVerificationTokenService;

    @BeforeEach
    void setUp() {
        emailVerificationTokenService = new EmailVerificationTokenService(
                emailVerificationTokenRepository,
                memberRepository,
                tokenGenerator,
                tokenHasher,
                new MailRateLimitProperties(
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(15),
                        5
                )
        );
    }

    @Test
    void 이메일_인증_토큰을_해시해_15분_유효기간으로_저장한다() {
        String rawToken = "raw-email-verification-token";
        String tokenHash = "hashed-email-verification-token";
        when(member.getId()).thenReturn(1L);
        when(member.getStatus()).thenReturn(MemberStatus.PENDING);
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(tokenGenerator.generate()).thenReturn(rawToken);
        when(tokenHasher.hash(rawToken)).thenReturn(tokenHash);

        Optional<String> issuedToken = emailVerificationTokenService.issue(member);

        ArgumentCaptor<LocalDateTime> issuedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<EmailVerificationToken> tokenCaptor =
                ArgumentCaptor.forClass(EmailVerificationToken.class);
        InOrder inOrder = inOrder(emailVerificationTokenRepository);
        inOrder.verify(emailVerificationTokenRepository)
                .revokeAllActiveByMemberId(eq(1L), issuedAtCaptor.capture());
        inOrder.verify(emailVerificationTokenRepository).save(tokenCaptor.capture());

        EmailVerificationToken savedToken = tokenCaptor.getValue();
        assertThat(issuedToken).contains(rawToken);
        assertThat(savedToken.getMember()).isSameAs(member);
        assertThat(savedToken.getTokenHash()).isEqualTo(tokenHash);
        assertThat(savedToken.getExpiresAt())
                .isEqualTo(issuedAtCaptor.getValue().plusMinutes(15));
        assertThat(savedToken.getUsedAt()).isNull();
        assertThat(savedToken.getRevokedAt()).isNull();
    }

    @Test
    void 쿨다운_중에는_이메일_인증_토큰을_발급하지_않는다() {
        when(member.getId()).thenReturn(1L);
        when(member.getStatus()).thenReturn(MemberStatus.PENDING);
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(emailVerificationTokenRepository.countIssuedSince(eq(1L), any(LocalDateTime.class)))
                .thenReturn(1L);

        Optional<String> issuedToken = emailVerificationTokenService.issue(member);

        assertThat(issuedToken).isEmpty();
        verifyNoInteractions(tokenGenerator, tokenHasher);
        verify(emailVerificationTokenRepository, never())
                .revokeAllActiveByMemberId(eq(1L), any(LocalDateTime.class));
    }

    @Test
    void 윈도우_내_최대_횟수에_도달하면_이메일_인증_토큰을_발급하지_않는다() {
        when(member.getId()).thenReturn(1L);
        when(member.getStatus()).thenReturn(MemberStatus.PENDING);
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(emailVerificationTokenRepository.countIssuedSince(eq(1L), any(LocalDateTime.class)))
                .thenReturn(0L, 5L);

        Optional<String> issuedToken = emailVerificationTokenService.issue(member);

        assertThat(issuedToken).isEmpty();
        verifyNoInteractions(tokenGenerator, tokenHasher);
        verify(emailVerificationTokenRepository, never())
                .revokeAllActiveByMemberId(eq(1L), any(LocalDateTime.class));
    }

    @Test
    void 유효한_토큰으로_인증하면_토큰을_사용_처리하고_회원을_활성화한다() {
        String rawToken = "raw-email-verification-token";
        String tokenHash = "hashed-email-verification-token";
        EmailVerificationToken token = EmailVerificationToken.issue(
                member,
                tokenHash,
                LocalDateTime.now().plusHours(1)
        );
        when(tokenHasher.hash(rawToken)).thenReturn(tokenHash);
        when(emailVerificationTokenRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.of(token));

        emailVerificationTokenService.verify(rawToken);

        verify(member).activate();
        assertThat(token.getUsedAt()).isNotNull();
    }

    @Test
    void 존재하지_않는_토큰으로는_인증할_수_없다() {
        String rawToken = "invalid-token";
        String tokenHash = "hashed-invalid-token";
        when(tokenHasher.hash(rawToken)).thenReturn(tokenHash);
        when(emailVerificationTokenRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.empty());

        AuthException exception = assertThrows(
                AuthException.class,
                () -> emailVerificationTokenService.verify(rawToken)
        );

        assertEquals(ErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN, exception.getErrorCode());
        verify(member, never()).activate();
    }

    @Test
    void 만료된_토큰으로는_인증할_수_없다() {
        String rawToken = "expired-token";
        String tokenHash = "hashed-expired-token";
        EmailVerificationToken token = EmailVerificationToken.issue(
                member,
                tokenHash,
                LocalDateTime.now().minusMinutes(1)
        );
        when(tokenHasher.hash(rawToken)).thenReturn(tokenHash);
        when(emailVerificationTokenRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.of(token));

        AuthException exception = assertThrows(
                AuthException.class,
                () -> emailVerificationTokenService.verify(rawToken)
        );

        assertEquals(ErrorCode.EXPIRED_EMAIL_VERIFICATION_TOKEN, exception.getErrorCode());
        verify(member, never()).activate();
    }

    @Test
    void 이미_사용한_토큰은_재사용할_수_없다() {
        String rawToken = "used-token";
        String tokenHash = "hashed-used-token";
        EmailVerificationToken token = EmailVerificationToken.issue(
                member,
                tokenHash,
                LocalDateTime.now().plusHours(1)
        );
        token.markUsed(LocalDateTime.now().minusSeconds(1));
        when(tokenHasher.hash(rawToken)).thenReturn(tokenHash);
        when(emailVerificationTokenRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.of(token));

        AuthException exception = assertThrows(
                AuthException.class,
                () -> emailVerificationTokenService.verify(rawToken)
        );

        assertEquals(ErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN, exception.getErrorCode());
        verify(member, never()).activate();
    }

    @Test
    void 폐기된_토큰은_사용할_수_없다() {
        String rawToken = "revoked-token";
        String tokenHash = "hashed-revoked-token";
        EmailVerificationToken token = mock(EmailVerificationToken.class);
        when(token.getRevokedAt()).thenReturn(LocalDateTime.now().minusSeconds(1));
        when(tokenHasher.hash(rawToken)).thenReturn(tokenHash);
        when(emailVerificationTokenRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.of(token));

        AuthException exception = assertThrows(
                AuthException.class,
                () -> emailVerificationTokenService.verify(rawToken)
        );

        assertEquals(ErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN, exception.getErrorCode());
        verify(member, never()).activate();
    }

    @Test
    void 동시_인증_요청으로_낙관적_락_충돌이_발생하면_잘못된_토큰으로_처리한다() {
        String rawToken = "raw-email-verification-token";
        String tokenHash = "hashed-email-verification-token";
        EmailVerificationToken token = EmailVerificationToken.issue(
                member,
                tokenHash,
                LocalDateTime.now().plusHours(1)
        );
        when(tokenHasher.hash(rawToken)).thenReturn(tokenHash);
        when(emailVerificationTokenRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.of(token));
        doThrow(new ObjectOptimisticLockingFailureException(EmailVerificationToken.class, 1L))
                .when(emailVerificationTokenRepository).flush();

        AuthException exception = assertThrows(
                AuthException.class,
                () -> emailVerificationTokenService.verify(rawToken)
        );

        assertEquals(ErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN, exception.getErrorCode());
    }
}
