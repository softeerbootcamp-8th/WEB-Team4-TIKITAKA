package com.tikitaka.bidwinback.mypage.presentation.dto.response;

public record MyBidRecordResponse(
        long auctionId,
        String title,
        String thumbnailUrl,
        long myBidAmount,
        long deadline,
        boolean isWinning,
        /** 마감 5분 전 밀봉 구간에 들어간 경매인지. 프론트가 타이머 옆에 "밀봉입찰중"을 표시하는 데만 쓴다. */
        boolean isSealedPhase,
        long biddedAt
) {
}
