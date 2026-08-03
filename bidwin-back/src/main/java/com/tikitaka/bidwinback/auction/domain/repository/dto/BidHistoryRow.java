package com.tikitaka.bidwinback.auction.domain.repository.dto;

import java.time.LocalDateTime;

public record BidHistoryRow(
        Long id,
        Long bidderId,
        String bidderNickname,
        long amount,
        LocalDateTime biddedAt
) {
}
