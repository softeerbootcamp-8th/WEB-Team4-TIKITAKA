package com.tikitaka.bidwinback.auction.domain.enums;

import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;

import java.util.Arrays;

public enum AuctionCategory{
    HOUSEHOLD("생활용품"),
    FOOD("먹거리"),
    FURNITURE("가구");

    private final String label;

    AuctionCategory(String label){
        this.label = label;
    }

    public String getLabel(){
        return this.label;
    }

    // 클라이언트로부터 들어온 카테고리의 String을 Enum값으로 바꿔주면서,
    // HOUSEHOLD, FOOD, FURNITURE에 해당하는지 유효성에 대한 검증까지 진행함
    public static AuctionCategory from(String code) {
        return Arrays.stream(values())
                .filter(category -> category.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new AuctionException(ErrorCode.CATEGORY_NOT_FOUND));
    }
}