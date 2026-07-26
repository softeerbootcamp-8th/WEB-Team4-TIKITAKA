package com.tikitaka.bidwinback.auth.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurePasswordResetTokenGeneratorTest {

    private final SecurePasswordResetTokenGenerator tokenGenerator =
            new SecurePasswordResetTokenGenerator();

    @Test
    void URL에_안전한_256비트_토큰을_생성한다() {
        String token = tokenGenerator.generate();

        assertThat(token).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void 매번_새로운_토큰을_생성한다() {
        String firstToken = tokenGenerator.generate();
        String secondToken = tokenGenerator.generate();

        assertThat(firstToken).isNotEqualTo(secondToken);
    }
}
