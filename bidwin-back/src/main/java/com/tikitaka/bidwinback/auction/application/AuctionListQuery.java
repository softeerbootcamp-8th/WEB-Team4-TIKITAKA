package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionListStatusFilter;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 컨트롤러가 쿼리 파라미터를 다듬어 넘기는 내부 전달용 객체.
 * asOf가 null이면 서비스가 DB 시각을 새로 찍어 이번 조회의 기준 시각으로 쓴다.
 * 추천순은 전달된 asOf와 무관하게 항상 DB 현재 시각을 사용한다.
 */
public record AuctionListQuery(
        AuctionType auctionType,
        AuctionSort sort,
        String keyword,
        AuctionListStatusFilter status,
        AuctionCategory category,
        int page,
        int size,
        LocalDateTime asOf
) {

    private static final int MIN_KEYWORD_LENGTH = 2;
    private static final int MAX_KEYWORD_LENGTH = 30;

    public AuctionListQuery {
        keyword = normalizeKeyword(keyword);
        if (keyword != null
                && (keyword.length() < MIN_KEYWORD_LENGTH
                || keyword.length() > MAX_KEYWORD_LENGTH)) {
            throw new AuctionException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "검색어는 2자 이상 30자 이하로 입력해주세요."
            );
        }
        if (keyword != null
                && (sort == AuctionSort.PRICE_LOW || sort == AuctionSort.PRICE_HIGH)) {
            throw new AuctionException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "검색 중에는 가격순 정렬을 사용할 수 없습니다."
            );
        }
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }
}
