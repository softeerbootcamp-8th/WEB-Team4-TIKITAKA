package com.tikitaka.bidwinback.auth.application;

public interface EmailVerificationMailSender {

    void send(String recipientEmail, String rawToken);
}
