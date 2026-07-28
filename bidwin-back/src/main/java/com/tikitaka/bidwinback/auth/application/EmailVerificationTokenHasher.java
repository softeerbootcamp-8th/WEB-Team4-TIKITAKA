package com.tikitaka.bidwinback.auth.application;

public interface EmailVerificationTokenHasher {

    String hash(String token);
}
