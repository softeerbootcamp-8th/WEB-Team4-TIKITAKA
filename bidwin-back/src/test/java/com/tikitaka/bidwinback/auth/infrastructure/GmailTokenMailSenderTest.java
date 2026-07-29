package com.tikitaka.bidwinback.auth.infrastructure;

import com.tikitaka.bidwinback.auth.domain.enums.MailPurpose;
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
class GmailTokenMailSenderTest {

    private static final String SENDER_EMAIL = "bidwin.test@gmail.com";
    private static final String PASSWORD_RESET_URL =
            "http://localhost:5173/password-reset";
    private static final String EMAIL_VERIFICATION_URL =
            "http://localhost:5173/email-verification";

    @Mock
    private JavaMailSender mailSender;

    private GmailTokenMailSender tokenMailSender;

    @BeforeEach
    void setUp() {
        tokenMailSender = new GmailTokenMailSender(
                mailSender,
                SENDER_EMAIL,
                PASSWORD_RESET_URL,
                EMAIL_VERIFICATION_URL
        );
    }

    @Test
    void 비밀번호_재설정_링크를_이메일로_발송한다() {
        String recipientEmail = "member@example.com";
        String rawToken = "raw-password-reset-token";

        tokenMailSender.send(MailPurpose.PASSWORD_RESET, recipientEmail, rawToken);

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

    @Test
    void 이메일_인증_링크를_이메일로_발송한다() {
        String recipientEmail = "member@example.com";
        String rawToken = "raw-email-verification-token";

        tokenMailSender.send(MailPurpose.EMAIL_VERIFICATION, recipientEmail, rawToken);

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
