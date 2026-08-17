package com.tikitaka.bidwinback.auth.application.emailverification;

import java.time.Duration;

/**
 * 인증 메일 토큰 발급 시도의 결과.
 * 발급에 성공하면 원본 토큰을, 재발급 제한에 걸리면 다음 발급까지 남은 시간을 담는다.
 */
public record EmailVerificationIssueResult(String rawToken, Duration retryAfter) {

    public static EmailVerificationIssueResult issued(String rawToken, Duration retryAfter) {
        return new EmailVerificationIssueResult(rawToken, retryAfter);
    }

    public static EmailVerificationIssueResult notIssued(Duration retryAfter) {
        return new EmailVerificationIssueResult(null, retryAfter);
    }

    public boolean isIssued() {
        return rawToken != null;
    }
}
