package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;

public enum AuctionListCountScope {
    ALL,
    UP,
    DOWN;

    public static AuctionListCountScope from(AuctionType auctionType) {
        if (auctionType == null) {
            return ALL;
        }
        return switch (auctionType) {
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }
}
