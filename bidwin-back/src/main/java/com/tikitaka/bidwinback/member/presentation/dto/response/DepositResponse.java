package com.tikitaka.bidwinback.member.presentation.dto.response;

public record DepositResponse(
        // 보증금 총액
        long balance,
        // 진행 중인 입찰·거래에 묶여 지금은 쓸 수 없는 금액
        long inUse
) {
}
