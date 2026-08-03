package com.tikitaka.bidwinback.auth.application;

import com.tikitaka.bidwinback.auth.domain.enums.MailPurpose;

public interface TokenMailSender {

    void send(MailPurpose purpose, String recipientEmail, String rawToken);
}
