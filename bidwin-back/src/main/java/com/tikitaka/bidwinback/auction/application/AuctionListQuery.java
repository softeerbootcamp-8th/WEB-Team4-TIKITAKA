package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionListStatusFilter;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;

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
        LocalDateTime asOf
) {
}
