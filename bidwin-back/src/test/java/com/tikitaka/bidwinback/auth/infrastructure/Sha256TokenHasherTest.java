package com.tikitaka.bidwinback.auth.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256TokenHasherTest {

    private final Sha256TokenHasher tokenHasher = new Sha256TokenHasher();

    @Test
    void 비밀번호_재설정_토큰을_SHA_256으로_해시한다() {
        String tokenHash = tokenHasher.hash("password-reset-token");

        assertThat(tokenHash).isEqualTo(
                "28a0861a36e29ed3dc289850a913d0843f694bd401cdf5c95130b3604e562204"
        );
    }

    @Test
    void 이메일_인증_토큰을_SHA_256으로_해시한다() {
        String tokenHash = tokenHasher.hash("email-verification-token");

        assertThat(tokenHash).isEqualTo(
                "99733c5e7e811c9496bce98ab56e775b891c27ddb509f00f000bb4bf478db406"
        );
    }
}
