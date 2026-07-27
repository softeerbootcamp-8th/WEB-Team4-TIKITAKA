package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.auth.domain.entity.PasswordResetToken;
import com.tikitaka.bidwinback.auth.domain.repository.PasswordResetTokenRepository;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetTokenServiceTest {

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordResetTokenGenerator tokenGenerator;

    @Mock
    private PasswordResetTokenHasher tokenHasher;

    @Mock
    private Member member;

    private PasswordResetTokenService passwordResetTokenService;

    @BeforeEach
    void setUp() {
        passwordResetTokenService = new PasswordResetTokenService(
                passwordResetTokenRepository,
                tokenGenerator,
                tokenHasher
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
}
