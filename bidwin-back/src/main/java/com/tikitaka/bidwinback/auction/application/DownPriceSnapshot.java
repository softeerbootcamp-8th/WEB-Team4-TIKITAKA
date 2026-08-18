package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record DownPriceSnapshot(
        LocalDateTime generationAt,
        List<AuctionPriceSnapshot> priceLow,
        List<AuctionPriceSnapshot> priceHigh
) {

    public static final int MAX_ENTRIES_PER_SORT = 1_600;

    public DownPriceSnapshot {
        Objects.requireNonNull(generationAt, "세대 시각은 필수입니다.");
        priceLow = List.copyOf(priceLow);
        priceHigh = List.copyOf(priceHigh);
        if (priceLow.size() > MAX_ENTRIES_PER_SORT
                || priceHigh.size() > MAX_ENTRIES_PER_SORT) {
            throw new IllegalArgumentException("정렬별 스냅샷은 1,600건을 초과할 수 없습니다.");
        }
        if (priceLow.size() != priceHigh.size()) {
            throw new IllegalArgumentException("LOW/HIGH 스냅샷 크기가 일치하지 않습니다.");
        }
    }

    public List<AuctionPriceSnapshot> entries(AuctionSort sort) {
        return switch (sort) {
            case PRICE_LOW -> priceLow;
            case PRICE_HIGH -> priceHigh;
            case RECOMMENDED, DEADLINE, LATEST ->
                    throw new IllegalArgumentException("가격 정렬이 아닙니다: " + sort);
        };
    }
}
