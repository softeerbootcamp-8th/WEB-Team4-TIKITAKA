package com.tikitaka.bidwinback.auth.infrastructure;

import com.tikitaka.bidwinback.auth.application.MailPurpose;
import com.tikitaka.bidwinback.auth.application.TokenMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class GmailTokenMailSender implements TokenMailSender {

    private record MailTemplate(String subject, String bodyTemplate, String url) {

        String createUrl(String rawToken) {
            return UriComponentsBuilder.fromUriString(url)
                    .queryParam("token", rawToken)
                    .build()
                    .encode()
                    .toUriString();
        }
    }

    private static final String PASSWORD_RESET_SUBJECT = "[BidWin] 비밀번호 재설정 안내";
    private static final String PASSWORD_RESET_BODY_TEMPLATE = """
            비밀번호 재설정을 요청하셨습니다.

            아래 링크를 통해 비밀번호를 재설정해 주세요.
            %s

            이 링크는 15분 동안 유효합니다.
            본인이 요청하지 않았다면 이 메일을 무시해 주세요.
            """;

    private static final String EMAIL_VERIFICATION_SUBJECT = "[BidWin] 이메일 인증 안내";
    private static final String EMAIL_VERIFICATION_BODY_TEMPLATE = """
            회원가입을 환영합니다!

            아래 링크를 통해 이메일 인증을 완료해 주세요.
            %s

            이 링크는 15분 동안 유효합니다.
            본인이 요청하지 않았다면 이 메일을 무시해 주세요.
            """;

    private final JavaMailSender mailSender;
    private final String senderEmail;
    private final Map<MailPurpose, MailTemplate> templates;

    public GmailTokenMailSender(
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String senderEmail,
            @Value("${app.password-reset.url}") String passwordResetUrl,
            @Value("${app.email-verification.url}") String emailVerificationUrl
    ) {
        this.mailSender = mailSender;
        this.senderEmail = senderEmail;
        this.templates = Map.of(
                MailPurpose.PASSWORD_RESET,
                new MailTemplate(PASSWORD_RESET_SUBJECT, PASSWORD_RESET_BODY_TEMPLATE, passwordResetUrl),
                MailPurpose.EMAIL_VERIFICATION,
                new MailTemplate(EMAIL_VERIFICATION_SUBJECT, EMAIL_VERIFICATION_BODY_TEMPLATE, emailVerificationUrl)
        );
    }

    @Override
    public void send(MailPurpose purpose, String recipientEmail, String rawToken) {
        MailTemplate template = templates.get(purpose);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(recipientEmail);
        message.setSubject(template.subject());
        message.setText(template.bodyTemplate().formatted(template.createUrl(rawToken)));

        mailSender.send(message);
    }
}
