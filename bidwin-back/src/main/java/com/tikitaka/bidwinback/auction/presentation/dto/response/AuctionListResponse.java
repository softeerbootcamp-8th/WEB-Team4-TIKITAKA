package com.tikitaka.bidwinback.auction.presentation.dto.response;

import java.util.List;

public record AuctionListResponse(
        List<AuctionSummaryResponse> items,
        // 응답 생성 시각. 클라이언트 시계 보정과 실시간 하락가 계산에 사용한다.
        long serverTime,
        // 목록을 계산한 스냅샷 시각. 다음 페이지 요청의 asOf로 그대로 보낸다.
        long asOf,
        int page,
        int totalPages,
        long totalCount
) {
}
