package com.tikitaka.bidwinback.auction.application.deposit;

// 보증금 충전 결과. 이전 예약 금액, 충전 후 예약 금액, 이번에 추가로 잠근 금액을 담는다.
public record DepositFundingResult(
        long previousReserved,
        long reservedAmount,
        long addedAmount
) {
}
