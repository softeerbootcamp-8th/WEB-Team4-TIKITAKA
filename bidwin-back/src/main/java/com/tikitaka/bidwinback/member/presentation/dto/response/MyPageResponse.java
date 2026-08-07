package com.tikitaka.bidwinback.member.presentation.dto.response;

import java.util.List;

/** 마이페이지 메인 화면 한 번에 필요한 다섯 블록을 담는 응답. */
public record MyPageResponse(
        ProfileResponse profile,
        DepositResponse deposit,
        List<ActiveTradeResponse> activeTrades,
        List<SellingItemResponse> sellingItems,
        List<BuyingItemResponse> buyingItems
) {
}
