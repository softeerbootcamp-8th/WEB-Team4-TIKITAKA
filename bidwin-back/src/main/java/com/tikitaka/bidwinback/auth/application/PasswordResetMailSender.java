package com.tikitaka.bidwinback.auth.application;

public interface PasswordResetMailSender {

    void send(String recipientEmail, String rawToken);
}
