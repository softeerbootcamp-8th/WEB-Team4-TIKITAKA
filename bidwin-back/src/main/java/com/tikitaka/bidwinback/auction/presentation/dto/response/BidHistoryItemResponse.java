package com.tikitaka.bidwinback.auction.presentation.dto.response;

import java.time.LocalDateTime;
import java.time.ZoneId;
import io.swagger.v3.oas.annotations.media.Schema;

public record BidHistoryItemResponse(
        @Schema(description = "공개·비공개 입찰을 구분하는 항목 ID", example = "BID:12")
        String entryId,
        @Schema(description = "마스킹된 입찰자 닉네임", example = "경*왕")
        String bidder,
        @Schema(description = "입찰 금액", example = "25000")
        long amount,
        @Schema(description = "입찰 시각(epoch milliseconds)", example = "1786860000000")
        long biddedAt
) {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    public static BidHistoryItemResponse of(
            String entryId,
            String bidderNickname,
            long amount,
            LocalDateTime biddedAt
    ) {
        return new BidHistoryItemResponse(
                entryId,
                maskNickname(bidderNickname),
                amount,
                biddedAt.atZone(SERVICE_ZONE).toInstant().toEpochMilli()
        );
    }

    private static String maskNickname(String nickname) {
        int nicknameLength = nickname.length();
        if (nicknameLength <= 1) {
            return "*";
        }
        if (nicknameLength == 2) {
            return nickname.substring(0, 1) + "*";
        }
        return nickname.substring(0, 1)
                + "*".repeat(nicknameLength - 2)
                + nickname.substring(nicknameLength - 1);
    }
}
