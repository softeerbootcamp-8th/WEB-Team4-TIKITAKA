package com.tikitaka.bidwinback.auth.presentation.dto.response;

import java.time.Duration;

/**
 * 인증 메일 발송 결과.
 * sent는 메일을 실제로 보냈는지, retryAfterSeconds는 다음 재전송까지 남은 시간(초)이다.
 */
public record EmailVerificationSendResponse(boolean sent, long retryAfterSeconds) {

    private static final long MILLIS_PER_SECOND = 1_000L;

    public static EmailVerificationSendResponse of(boolean sent, Duration retryAfter) {
        return new EmailVerificationSendResponse(sent, toWaitSeconds(retryAfter));
    }

    // 남은 시간을 내림하면 카운트다운이 0이 된 뒤에도 제한에 걸리므로 올림해서 내려준다.
    private static long toWaitSeconds(Duration retryAfter) {
        if (retryAfter.isNegative() || retryAfter.isZero()) {
            return 0L;
        }
        return (retryAfter.toMillis() + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND;
    }
}
