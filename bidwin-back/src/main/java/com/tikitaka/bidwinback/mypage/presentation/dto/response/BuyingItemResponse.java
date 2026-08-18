package com.tikitaka.bidwinback.mypage.presentation.dto.response;

public record BuyingItemResponse(
        Long auctionId,
        String title,
        String thumbnailUrl,
        // 경매 유형: UP | DOWN
        String auctionType,
        long startPrice,
        // 최종 거래가
        long price,
        // 구매 상태: PAYMENT_PENDING | IN_PROGRESS | DONE
        String status
) {
}
