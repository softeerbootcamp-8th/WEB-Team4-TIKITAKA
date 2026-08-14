package com.tikitaka.bidwinback.auction.domain.enums;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;

public enum AuctionType {
    UP,
    DOWN;

    public static AuctionType from(Auction auction) {
        if (auction instanceof UpAuction) {
            return UP;
        }
        if (auction instanceof DownAuction) {
            return DOWN;
        }
        throw new IllegalStateException("지원하지 않는 경매 유형입니다: " + auction.getClass());
    }
}
