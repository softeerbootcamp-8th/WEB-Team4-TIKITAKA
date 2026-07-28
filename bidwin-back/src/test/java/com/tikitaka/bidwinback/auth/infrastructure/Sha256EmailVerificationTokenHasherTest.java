package com.tikitaka.bidwinback.auth.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256EmailVerificationTokenHasherTest {

    private final Sha256EmailVerificationTokenHasher tokenHasher =
            new Sha256EmailVerificationTokenHasher();

    @Test
    void 토큰을_SHA_256으로_해시한다() {
        String tokenHash = tokenHasher.hash("email-verification-token");

        assertThat(tokenHash).isEqualTo(
                "99733c5e7e811c9496bce98ab56e775b891c27ddb509f00f000bb4bf478db406"
        );
    }
}
