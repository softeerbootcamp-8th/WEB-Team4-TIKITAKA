package com.tikitaka.bidwinback.auth.infrastructure;

import com.tikitaka.bidwinback.auth.application.PasswordHasher;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class Pbkdf2PasswordHasher implements PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2-sha256";
    private static final int ITERATIONS = 600_000;
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String hash(String rawPassword) {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        byte[] hash = derive(rawPassword, salt);

        Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
        return String.join(
                "$",
                PREFIX,
                String.valueOf(ITERATIONS),
                encoder.encodeToString(salt),
                encoder.encodeToString(hash)
        );
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }

        String[] parts = encodedPassword.split("\\$", -1);
        if (parts.length != 4
                || !PREFIX.equals(parts[0])
                || !String.valueOf(ITERATIONS).equals(parts[1])) {
            return false;
        }

        try {
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] salt = decoder.decode(parts[2]);
            byte[] expectedHash = decoder.decode(parts[3]);
            if (salt.length != SALT_LENGTH || expectedHash.length != HASH_LENGTH) {
                return false;
            }

            return MessageDigest.isEqual(expectedHash, derive(rawPassword, salt));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] derive(String rawPassword, byte[] salt) {
        PBEKeySpec keySpec = new PBEKeySpec(
                rawPassword.toCharArray(),
                salt,
                ITERATIONS,
                HASH_LENGTH * Byte.SIZE
        );

        try {
            return SecretKeyFactory.getInstance(ALGORITHM)
                    .generateSecret(keySpec)
                    .getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("PBKDF2 비밀번호 해싱에 실패했습니다.", exception);
        } finally {
            keySpec.clearPassword();
        }
    }
}
