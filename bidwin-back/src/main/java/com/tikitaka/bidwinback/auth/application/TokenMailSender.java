package com.tikitaka.bidwinback.auth.application;

public interface TokenMailSender {

    void send(MailPurpose purpose, String recipientEmail, String rawToken);
}
