package com.tikitaka.bidwinback.auth.infrastructure;

import com.tikitaka.bidwinback.auth.application.PasswordResetMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GmailPasswordResetMailSender implements PasswordResetMailSender {

    private static final String SUBJECT = "[BidWin] 비밀번호 재설정 안내";
    private static final String BODY_TEMPLATE = """
            비밀번호 재설정을 요청하셨습니다.

            아래 링크를 통해 비밀번호를 재설정해 주세요.
            %s

            이 링크는 15분 동안 유효합니다.
            본인이 요청하지 않았다면 이 메일을 무시해 주세요.
            """;

    private final JavaMailSender mailSender;
    private final String senderEmail;
    private final String passwordResetUrl;

    public GmailPasswordResetMailSender(
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String senderEmail,
            @Value("${app.password-reset.url}") String passwordResetUrl
    ) {
        this.mailSender = mailSender;
        this.senderEmail = senderEmail;
        this.passwordResetUrl = passwordResetUrl;
    }

    @Override
    public void send(String recipientEmail, String rawToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(recipientEmail);
        message.setSubject(SUBJECT);
        message.setText(BODY_TEMPLATE.formatted(createResetUrl(rawToken)));

        mailSender.send(message);
    }

    private String createResetUrl(String rawToken) {
        return UriComponentsBuilder.fromUriString(passwordResetUrl)
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();
    }
}
