package com.tikitaka.bidwinback.auction.presentation.dto.response;

import java.util.List;

public record AuctionListResponse(
        List<AuctionSummaryResponse> items,
        // 이 목록을 계산한 기준 시각(ms). 다음 페이지 요청 시 asOf로 그대로 돌려보내면
        // 페이지를 넘기는 동안 하락 경매 가격·정렬 순서가 흔들리지 않는다.
        long serverTime,
        int page,
        int totalPages,
        long totalCount
) {
}
