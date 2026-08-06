package com.tikitaka.bidwinback.auction.application;

public interface DepositSettlementService {

    // 해당 경매의 보증금을 목표 금액까지 올린다. 회원 전체 잠금액에는 차액만 더한다.
    DepositFundingResult topUpToFinalPrice(
            Long auctionId,
            Long buyerId,
            long targetAmount
    );

    // 경매·구매자의 보증금을 반환한다. 잠금액을 사용 가능 잔액으로 되돌린다.
    void refund(
            Long auctionId,
            Long buyerId,
            long expectedAmount
    );

    // 경매·구매자의 보증금을 판매자에게 지급한다. 구매자 잠금액을 판매자 잔액으로 옮긴다.
    void transferToSeller(
            Long auctionId,
            Long buyerId,
            Long sellerId,
            long expectedAmount
    );

    // 경매·구매자의 보증금을 몰수한다. 잠금액을 차감하고 되돌리지 않는다.
    void forfeit(
            Long auctionId,
            Long buyerId,
            long expectedAmount
    );
}
