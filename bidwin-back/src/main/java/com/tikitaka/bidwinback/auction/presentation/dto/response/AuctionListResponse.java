package com.tikitaka.bidwinback.auction.presentation.dto.response;

import java.util.List;

public record AuctionListResponse(
        List<AuctionSummaryResponse> items,
        // 응답 생성 시각. 클라이언트 시계 보정과 실시간 하락가 계산에 사용한다.
        long serverTime,
        // 목록 계산 기준 시각. 추천순은 요청마다 DB 현재 시각을 새로 사용한다.
        long asOf,
        int page,
        int totalPages,
        long totalCount
) {
}
