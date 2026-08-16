package com.tikitaka.bidwinback.auction.domain.enums;

import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;

import java.util.Arrays;

public enum AuctionDuration {
    MINUTES_6(6),
    MINUTES_30(30),
    HOUR_1(60),
    HOURS_3(180),
    HOURS_6(360);

    private final int minutes;

    AuctionDuration(int minutes) {
        this.minutes = minutes;
    }

    public int getMinutes() {
        return minutes;
    }

    // 클라이언트가 보낸 분(minute) 값이 6/30/60/180/360 중 하나인지 검증하면서 Enum으로 바꿔준다.
    public static AuctionDuration from(Integer minutes) {
        if (minutes == null) {
            throw new AuctionException(ErrorCode.INVALID_DURATION);
        }

        return Arrays.stream(values())
                .filter(duration -> duration.minutes == minutes)
                .findFirst()
                .orElseThrow(() -> new AuctionException(ErrorCode.INVALID_DURATION));
    }
}
