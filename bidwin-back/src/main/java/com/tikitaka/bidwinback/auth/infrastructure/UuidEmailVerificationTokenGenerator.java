package com.tikitaka.bidwinback.auth.infrastructure;

import com.tikitaka.bidwinback.auth.application.EmailVerificationTokenGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UuidEmailVerificationTokenGenerator implements EmailVerificationTokenGenerator {

    @Override
    public String generate() {
        return UUID.randomUUID().toString();
    }
}
