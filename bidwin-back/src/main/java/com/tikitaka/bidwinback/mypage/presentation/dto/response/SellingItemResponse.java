package com.tikitaka.bidwinback.mypage.presentation.dto.response;

public record SellingItemResponse(
        Long auctionId,
        String title,
        String thumbnailUrl,
        // 경매 유형: UP | DOWN
        String auctionType,
        long startPrice,
        // 진행 중이면 현재가, 끝났으면 최종 거래가
        long price,
        // 판매 상태: ON_SALE | SOLD | FAILED
        String status,
        // 하향 경매의 현재가를 클라이언트에서 계산할 때 사용한다.
        DownPricingResponse downPricing
) {
}
