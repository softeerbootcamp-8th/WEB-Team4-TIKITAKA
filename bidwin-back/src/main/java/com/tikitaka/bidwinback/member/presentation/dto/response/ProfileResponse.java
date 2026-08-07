package com.tikitaka.bidwinback.member.presentation.dto.response;

public record ProfileResponse(
        String nickname,
        String profileImageUrl,
        // 가입 시각 (epoch ms)
        long joinedAt,
        long sellCount,
        long auctionJoinCount
) {
}
