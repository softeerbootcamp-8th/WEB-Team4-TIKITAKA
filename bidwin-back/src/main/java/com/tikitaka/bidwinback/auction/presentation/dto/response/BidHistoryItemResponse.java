package com.tikitaka.bidwinback.auction.presentation.dto.response;

import java.time.LocalDateTime;
import java.time.ZoneId;

public record BidHistoryItemResponse(
        String entryId,
        String bidder,
        long amount,
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
