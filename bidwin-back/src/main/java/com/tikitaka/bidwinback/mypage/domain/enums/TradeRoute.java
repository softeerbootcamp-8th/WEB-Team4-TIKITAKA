package com.tikitaka.bidwinback.mypage.domain.enums;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;

/** 구매까지 이어진 경로. 상향 경매는 낙찰로, 하향 경매는 즉시구매로만 거래가 성립한다. */
public enum TradeRoute {
    WON,
    BUY_NOW;

    public static TradeRoute from(AuctionType auctionType) {
        return switch (auctionType) {
            case UP -> WON;
            case DOWN -> BUY_NOW;
        };
    }
}
