package com.tikitaka.bidwinback.auth.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UuidEmailVerificationTokenGeneratorTest {

    private final UuidEmailVerificationTokenGenerator tokenGenerator =
            new UuidEmailVerificationTokenGenerator();

    @Test
    void UUID_형식의_토큰을_생성한다() {
        String token = tokenGenerator.generate();

        assertThat(token).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        );
    }

    @Test
    void 매번_새로운_토큰을_생성한다() {
        String firstToken = tokenGenerator.generate();
        String secondToken = tokenGenerator.generate();

        assertThat(firstToken).isNotEqualTo(secondToken);
    }
}
