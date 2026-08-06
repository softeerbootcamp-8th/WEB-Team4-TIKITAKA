package com.tikitaka.bidwinback.auction.application;

public interface DepositSettlementService {

    // 해당 경매의 보증금을 목표 금액까지 올린다. 회원 전체 잠금액에는 차액만 더한다.
    DepositFundingResult topUpToFinalPrice(
            Long auctionId,
            Long buyerId,
            long targetAmount
    );
}
