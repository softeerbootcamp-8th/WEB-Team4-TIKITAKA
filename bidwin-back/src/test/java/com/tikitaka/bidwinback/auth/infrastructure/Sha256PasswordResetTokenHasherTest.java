package com.tikitaka.bidwinback.auth.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256PasswordResetTokenHasherTest {

    private final Sha256PasswordResetTokenHasher tokenHasher =
            new Sha256PasswordResetTokenHasher();

    @Test
    void 토큰을_SHA_256으로_해시한다() {
        String tokenHash = tokenHasher.hash("password-reset-token");

        assertThat(tokenHash).isEqualTo(
                "28a0861a36e29ed3dc289850a913d0843f694bd401cdf5c95130b3604e562204"
        );
    }
}
