package com.tikitaka.bidwinback.auth.infrastructure;

import com.tikitaka.bidwinback.auth.application.EmailVerificationTokenHasher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class Sha256EmailVerificationTokenHasher implements EmailVerificationTokenHasher {

    private static final String HASH_ALGORITHM = "SHA-256";

    @Override
    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashedToken = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashedToken);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 해시 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
