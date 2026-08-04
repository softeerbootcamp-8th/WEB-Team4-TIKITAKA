package com.tikitaka.bidwinback.global.auth;

import java.time.Instant;

/**
 * 프로덕션 코드에 authVersion/loggedInAt이 생략된 생성자를 두지 않기 위한 테스트 전용 픽스처.
 */
public final class AuthMemberFixture {

    private AuthMemberFixture() {
    }

    public static AuthMember of(long memberId) {
        return of(memberId, Instant.now());
    }

    public static AuthMember of(long memberId, Instant loggedInAt) {
        return of(memberId, 0L, loggedInAt);
    }

    public static AuthMember of(long memberId, long authVersion, Instant loggedInAt) {
        return new AuthMember(memberId, authVersion, loggedInAt);
    }
}
