package com.tikitaka.bidwinback.auction.application.live;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;

/** 커밋 후 전파할 거래 상태의 절대 스냅샷. */
public record TradeStatusChanged(TradeLiveState state) {

    public static TradeStatusChanged from(AuctionTrade trade) {
        return new TradeStatusChanged(new TradeLiveState(
                trade.getId(),
                trade.getAuction().getId(),
                trade.getStatus()
        ));
    }
}
