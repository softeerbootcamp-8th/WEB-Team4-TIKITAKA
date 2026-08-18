package com.tikitaka.bidwinback.auction.infrastructure;

final class AuctionListFullTextQuery {

    private static final int MIN_KEYWORD_LENGTH = 2;
    private static final int MAX_KEYWORD_LENGTH = 30;

    private final String value;

    private AuctionListFullTextQuery(String value) {
        this.value = value;
    }

    static AuctionListFullTextQuery from(String keyword) {
        if (keyword == null
                || keyword.length() < MIN_KEYWORD_LENGTH
                || keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new IllegalArgumentException("검색어는 2자 이상 30자 이하여야 합니다.");
        }

        String escaped = keyword
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        return new AuctionListFullTextQuery("\"" + escaped + "\"");
    }

    String value() {
        return value;
    }
}
