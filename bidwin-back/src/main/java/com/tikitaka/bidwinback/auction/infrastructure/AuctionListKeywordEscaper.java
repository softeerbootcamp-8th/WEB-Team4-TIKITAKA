package com.tikitaka.bidwinback.auction.infrastructure;

final class AuctionListKeywordEscaper {

    static final char LIKE_ESCAPE = '!';

    private AuctionListKeywordEscaper() {
    }

    static String escape(String keyword) {
        return keyword
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
