package com.tikitaka.bidwinback.member.presentation.dto.response;

public record ActiveTradeResponse(
        Long tradeId,
        Long auctionId,
        String title,
        String thumbnailUrl,
        // 이 거래에서 내가 맡은 쪽: BUYER | SELLER
        String role,
        // 거래 단계: PAYMENT_PENDING | IN_PROGRESS
        String status,
        long price
) {
}
