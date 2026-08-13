package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 컨트롤러가 쿼리 파라미터를 다듬어 넘기는 내부 전달용 객체.
 * asOf가 null이면 서비스가 DB 시각을 새로 찍어 이번 조회의 기준 시각으로 쓴다.
 */
public record AuctionListQuery(
        AuctionType auctionType,
        AuctionSort sort,
        String keyword,
        StatusFilter status,
        List<AuctionCategory> categories,
        int page,
        int size,
        LocalDateTime asOf
) {
    public enum StatusFilter {
        ACTIVE,
        ENDED
    }
}
