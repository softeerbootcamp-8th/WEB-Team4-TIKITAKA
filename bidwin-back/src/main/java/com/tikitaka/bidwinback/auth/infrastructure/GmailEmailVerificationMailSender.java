package com.tikitaka.bidwinback.auth.infrastructure;

import com.tikitaka.bidwinback.auth.application.EmailVerificationMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GmailEmailVerificationMailSender implements EmailVerificationMailSender {

    private static final String SUBJECT = "[BidWin] 이메일 인증 안내";
    private static final String BODY_TEMPLATE = """
            회원가입을 환영합니다!

            아래 링크를 통해 이메일 인증을 완료해 주세요.
            %s

            이 링크는 15분 동안 유효합니다.
            본인이 요청하지 않았다면 이 메일을 무시해 주세요.
            """;

    private final JavaMailSender mailSender;
    private final String senderEmail;
    private final String emailVerificationUrl;

    public GmailEmailVerificationMailSender(
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String senderEmail,
            @Value("${app.email-verification.url}") String emailVerificationUrl
    ) {
        this.mailSender = mailSender;
        this.senderEmail = senderEmail;
        this.emailVerificationUrl = emailVerificationUrl;
    }

    @Override
    public void send(String recipientEmail, String rawToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(recipientEmail);
        message.setSubject(SUBJECT);
        message.setText(BODY_TEMPLATE.formatted(createVerificationUrl(rawToken)));

        mailSender.send(message);
    }

    private String createVerificationUrl(String rawToken) {
        return UriComponentsBuilder.fromUriString(emailVerificationUrl)
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();
    }
}
