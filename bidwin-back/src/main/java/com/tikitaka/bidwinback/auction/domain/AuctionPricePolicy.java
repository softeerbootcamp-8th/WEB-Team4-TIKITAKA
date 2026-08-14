package com.tikitaka.bidwinback.auction.domain;

public final class AuctionPricePolicy {

    public static final long MAX_PRICE_EXCLUSIVE = 100_000_000_000L;

    private AuctionPricePolicy() {
    }

    public static boolean isAllowed(long price) {
        return price < MAX_PRICE_EXCLUSIVE;
    }
}
