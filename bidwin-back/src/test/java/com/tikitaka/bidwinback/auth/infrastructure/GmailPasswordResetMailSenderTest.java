package com.tikitaka.bidwinback.auth.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GmailPasswordResetMailSenderTest {

    private static final String SENDER_EMAIL = "bidwin.test@gmail.com";
    private static final String PASSWORD_RESET_URL =
            "http://localhost:5173/password-reset";

    @Mock
    private JavaMailSender mailSender;

    private GmailPasswordResetMailSender passwordResetMailSender;

    @BeforeEach
    void setUp() {
        passwordResetMailSender = new GmailPasswordResetMailSender(
                mailSender,
                SENDER_EMAIL,
                PASSWORD_RESET_URL
        );
    }

    @Test
    void 비밀번호_재설정_링크를_이메일로_발송한다() {
        String recipientEmail = "member@example.com";
        String rawToken = "raw-password-reset-token";

        passwordResetMailSender.send(recipientEmail, rawToken);

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message.getFrom()).isEqualTo(SENDER_EMAIL);
        assertThat(message.getTo()).containsExactly(recipientEmail);
        assertThat(message.getSubject()).isEqualTo("[BidWin] 비밀번호 재설정 안내");
        assertThat(message.getText())
                .contains(PASSWORD_RESET_URL + "?token=" + rawToken)
                .contains("15분 동안 유효합니다.");
    }
}
