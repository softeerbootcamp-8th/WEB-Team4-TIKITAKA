package com.tikitaka.bidwinback.auction.domain.enums;

import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;

import java.util.Arrays;

/**
 * 프론트 SortKey(recommended/deadline/latest/priceLow/priceHigh)와 1:1 대응한다.
 * 대소문자·언더스코어 표기가 다르니 enum 이름이 아니라 이 wireValue로 매칭한다.
 */
public enum AuctionSort {
    RECOMMENDED("recommended"),
    DEADLINE("deadline"),
    LATEST("latest"),
    PRICE_LOW("priceLow"),
    PRICE_HIGH("priceHigh");

    private static final AuctionSort DEFAULT = RECOMMENDED;

    private final String wireValue;

    AuctionSort(String wireValue) {
        this.wireValue = wireValue;
    }

    // 클라이언트가 안 보내면 기본값(추천순)으로, 값이 있는데 못 알아들으면 검증 에러로 처리한다.
    public static AuctionSort from(String code) {
        if (code == null || code.isBlank()) {
            return DEFAULT;
        }
        return Arrays.stream(values())
                .filter(sort -> sort.wireValue.equals(code))
                .findFirst()
                .orElseThrow(() -> new AuctionException(ErrorCode.INVALID_SORT));
    }
}
