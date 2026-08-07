package com.tikitaka.bidwinback.mypage.presentation.dto.response;

import com.tikitaka.bidwinback.auction.domain.enums.DepositStatus;

public record MyDepositRecordResponse(
        long depositId,
        long auctionId,
        String auctionTitle,
        long amount,
        DepositStatus status,
        long changedAt
) {
}
