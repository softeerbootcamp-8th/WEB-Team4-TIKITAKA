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
class GmailEmailVerificationMailSenderTest {

    private static final String SENDER_EMAIL = "bidwin.test@gmail.com";
    private static final String EMAIL_VERIFICATION_URL =
            "http://localhost:5173/email-verification";

    @Mock
    private JavaMailSender mailSender;

    private GmailEmailVerificationMailSender emailVerificationMailSender;

    @BeforeEach
    void setUp() {
        emailVerificationMailSender = new GmailEmailVerificationMailSender(
                mailSender,
                SENDER_EMAIL,
                EMAIL_VERIFICATION_URL
        );
    }

    @Test
    void 이메일_인증_링크를_이메일로_발송한다() {
        String recipientEmail = "member@example.com";
        String rawToken = "raw-email-verification-token";

        emailVerificationMailSender.send(recipientEmail, rawToken);

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message.getFrom()).isEqualTo(SENDER_EMAIL);
        assertThat(message.getTo()).containsExactly(recipientEmail);
        assertThat(message.getSubject()).isEqualTo("[BidWin] 이메일 인증 안내");
        assertThat(message.getText())
                .contains(EMAIL_VERIFICATION_URL + "?token=" + rawToken)
                .contains("15분 동안 유효합니다.");
    }
}
