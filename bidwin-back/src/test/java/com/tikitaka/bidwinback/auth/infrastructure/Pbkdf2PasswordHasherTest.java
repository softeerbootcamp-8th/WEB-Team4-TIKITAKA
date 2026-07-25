package com.tikitaka.bidwinback.auth.infrastructure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pbkdf2PasswordHasherTest {

    private final Pbkdf2PasswordHasher passwordHasher = new Pbkdf2PasswordHasher();

    @Test
    void 비밀번호를_고유한_salt로_해싱하고_검증한다() {
        String password = "password!";

        String firstHash = passwordHasher.hash(password);
        String secondHash = passwordHasher.hash(password);

        assertNotEquals(password, firstHash);
        assertNotEquals(firstHash, secondHash);
        assertTrue(passwordHasher.matches(password, firstHash));
        assertFalse(passwordHasher.matches("wrong-password!", firstHash));
        assertTrue(firstHash.length() <= 128);
    }

    @Test
    void 잘못된_해시_형식은_일치하지_않는다() {
        assertFalse(passwordHasher.matches("password!", "invalid-hash"));
        assertFalse(passwordHasher.matches("password!", null));
    }
}
