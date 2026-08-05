package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.auth.domain.enums.MailPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenMailDispatcher {

    private final TokenMailSender tokenMailSender;

    @Async("mailTaskExecutor")
    public void send(MailPurpose purpose, String recipientEmail, String rawToken) {
        tokenMailSender.send(purpose, recipientEmail, rawToken);
    }
}
