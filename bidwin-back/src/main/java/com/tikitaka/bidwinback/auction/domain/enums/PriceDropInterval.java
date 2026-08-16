package com.tikitaka.bidwinback.auction.domain.enums;

import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;

import java.util.Arrays;

public enum PriceDropInterval {
    MINUTE_1(1),
    MINUTES_3(3),
    MINUTES_5(5),
    MINUTES_10(10);

    private final int minutes;

    PriceDropInterval(int minutes) {
        this.minutes = minutes;
    }

    public int getMinutes() {
        return minutes;
    }

    // 클라이언트가 보낸 분(minute) 값이 1/3/5/10 중 하나인지 검증하면서 Enum으로 바꿔준다.
    public static PriceDropInterval from(Long minutes) {
        if (minutes == null) {
            throw new AuctionException(ErrorCode.INVALID_PRICE_DROP_INTERVAL);
        }

        return Arrays.stream(values())
                .filter(interval -> interval.minutes == minutes)
                .findFirst()
                .orElseThrow(() -> new AuctionException(ErrorCode.INVALID_PRICE_DROP_INTERVAL));
    }
}
