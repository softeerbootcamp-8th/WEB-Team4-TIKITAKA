package com.tikitaka.bidwinback.auth.application;

public interface TokenHasher {

    String hash(String token);
}
