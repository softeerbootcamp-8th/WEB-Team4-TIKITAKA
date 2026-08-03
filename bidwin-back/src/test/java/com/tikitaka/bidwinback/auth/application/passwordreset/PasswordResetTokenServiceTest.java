package com.tikitaka.bidwinback.auth.application.passwordreset;

import com.tikitaka.bidwinback.auth.application.PasswordHasher;
import com.tikitaka.bidwinback.auth.application.TokenGenerator;
import com.tikitaka.bidwinback.auth.application.TokenHasher;
import com.tikitaka.bidwinback.auth.domain.entity.PasswordResetToken;
import com.tikitaka.bidwinback.auth.domain.repository.PasswordResetTokenRepository;
import com.tikitaka.bidwinback.global.auth.exception.AuthException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetTokenServiceTest {

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private TokenGenerator tokenGenerator;

    @Mock
    private TokenHasher tokenHasher;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private Member member;

    private PasswordResetTokenService passwordResetTokenService;

    @BeforeEach
    void setUp() {
        passwordResetTokenService = new PasswordResetTokenService(
                passwordResetTokenRepository,
                tokenGenerator,
                tokenHasher,
                passwordHasher
        );
    }

    @Test
    void 비밀번호_재설정_토큰을_해시해_15분_유효기간으로_저장한다() {
        String rawToken = "raw-password-reset-token";
        String tokenHash = "hashed-password-reset-token";
        when(member.getId()).thenReturn(1L);
        when(tokenGenerator.generate()).thenReturn(rawToken);
        when(tokenHasher.hash(rawToken)).thenReturn(tokenHash);

        String issuedToken = passwordResetTokenService.issue(member);

        ArgumentCaptor<LocalDateTime> issuedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        InOrder inOrder = inOrder(passwordResetTokenRepository);
        inOrder.verify(passwordResetTokenRepository)
                .revokeAllActiveByMemberId(eq(1L), issuedAtCaptor.capture());
        inOrder.verify(passwordResetTokenRepository).save(tokenCaptor.capture());

        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertThat(issuedToken).isEqualTo(rawToken);
        assertThat(savedToken.getMember()).isSameAs(member);
        assertThat(savedToken.getTokenHash()).isEqualTo(tokenHash);
        assertThat(savedToken.getExpiresAt())
                .isEqualTo(issuedAtCaptor.getValue().plusMinutes(15));
        assertThat(savedToken.getUsedAt()).isNull();
        assertThat(savedToken.getRevokedAt()).isNull();
    }

    @Test
    void 유효한_토큰으로_비밀번호를_변경하고_토큰을_사용_처리한다() {
        String rawToken = "raw-password-reset-token";
        String tokenHash = "hashed-password-reset-token";
        PasswordResetToken token = PasswordResetToken.issue(
                member,
                tokenHash,
                LocalDateTime.now().plusMinutes(5)
        );
        when(tokenHasher.hash(rawToken)).thenReturn(tokenHash);
        when(passwordResetTokenRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.of(token));
        when(passwordHasher.hash("new-password!")).thenReturn("encoded-new-password");

        passwordResetTokenService.resetPassword(rawToken, "new-password!");

        verify(member).changePassword("encoded-new-password");
        assertThat(token.getUsedAt()).isNotNull();
    }

    @Test
    void 존재하지_않는_토큰으로는_비밀번호를_변경할_수_없다() {
        String rawToken = "invalid-token";
        String tokenHash = "hashed-invalid-token";
        when(tokenHasher.hash(rawToken)).thenReturn(tokenHash);
        when(passwordResetTokenRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.empty());

        AuthException exception = assertThrows(
                AuthException.class,
                () -> passwordResetTokenService.resetPassword(rawToken, "new-password!")
        );

        assertEquals(ErrorCode.INVALID_PASSWORD_RESET_TOKEN, exception.getErrorCode());
        verifyNoInteractions(passwordHasher);
    }

    @Test
    void 만료된_토큰으로는_비밀번호를_변경할_수_없다() {
        String rawToken = "expired-token";
        String tokenHash = "hashed-expired-token";
        PasswordResetToken token = PasswordResetToken.issue(
                member,
                tokenHash,
                LocalDateTime.now().minusMinutes(1)
        );
        when(tokenHasher.hash(rawToken)).thenReturn(tokenHash);
        when(passwordResetTokenRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.of(token));

        AuthException exception = assertThrows(
                AuthException.class,
                () -> passwordResetTokenService.resetPassword(rawToken, "new-password!")
        );

        assertEquals(ErrorCode.EXPIRED_PASSWORD_RESET_TOKEN, exception.getErrorCode());
        verifyNoInteractions(passwordHasher);
    }

    @Test
    void 이미_사용한_토큰은_재사용할_수_없다() {
        String rawToken = "used-token";
        String tokenHash = "hashed-used-token";
        PasswordResetToken token = PasswordResetToken.issue(
                member,
                tokenHash,
                LocalDateTime.now().plusMinutes(5)
        );
        token.markUsed(LocalDateTime.now().minusSeconds(1));
        when(tokenHasher.hash(rawToken)).thenReturn(tokenHash);
        when(passwordResetTokenRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.of(token));

        AuthException exception = assertThrows(
                AuthException.class,
                () -> passwordResetTokenService.resetPassword(rawToken, "new-password!")
        );

        assertEquals(ErrorCode.INVALID_PASSWORD_RESET_TOKEN, exception.getErrorCode());
        verifyNoInteractions(passwordHasher);
    }

    @Test
    void 폐기된_토큰은_사용할_수_없다() {
        String rawToken = "revoked-token";
        String tokenHash = "hashed-revoked-token";
        PasswordResetToken token = mock(PasswordResetToken.class);
        when(token.getRevokedAt()).thenReturn(LocalDateTime.now().minusSeconds(1));
        when(tokenHasher.hash(rawToken)).thenReturn(tokenHash);
        when(passwordResetTokenRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.of(token));

        AuthException exception = assertThrows(
                AuthException.class,
                () -> passwordResetTokenService.resetPassword(rawToken, "new-password!")
        );

        assertEquals(ErrorCode.INVALID_PASSWORD_RESET_TOKEN, exception.getErrorCode());
        verifyNoInteractions(passwordHasher);
    }
}
