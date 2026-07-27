package com.tikitaka.bidwinback.auth.application;

public interface PasswordResetTokenHasher {

    String hash(String token);
}
