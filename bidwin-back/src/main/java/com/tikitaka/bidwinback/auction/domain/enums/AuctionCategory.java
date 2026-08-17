package com.tikitaka.bidwinback.auction.domain.enums;

import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;

import java.util.Arrays;

public enum AuctionCategory{
    HOUSEHOLD("생활용품"),
    FOOD("먹거리"),
    FURNITURE("가구"),
    ELECTRONICS("디지털/가전"),
    FASHION("패션/잡화"),
    SPORTS("스포츠/레저"),
    HOBBY("취미/수집"),
    BOOK("도서/문구"),
    OTHER("기타");

    private final String label;

    AuctionCategory(String label){
        this.label = label;
    }

    public String getLabel(){
        return this.label;
    }

    // 클라이언트로부터 들어온 카테고리의 String을 Enum값으로 바꾸면서 유효성을 검증한다.
    public static AuctionCategory from(String code) {
        return Arrays.stream(values())
                .filter(category -> category.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new AuctionException(ErrorCode.CATEGORY_NOT_FOUND));
    }
}
