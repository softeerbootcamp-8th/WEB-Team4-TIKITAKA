package com.tikitaka.bidwinback.mypage.presentation.dto.response;

public record ProfileResponse(
        String nickname,
        String profileImageUrl,
        // 가입 시각 (epoch ms)
        long joinedAt,
        long sellCount,
        long auctionJoinCount
) {
}
