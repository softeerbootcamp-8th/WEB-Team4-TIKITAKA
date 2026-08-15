package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record DownPriceSnapshot(
        LocalDateTime snapshotAt,
        long totalCount,
        List<AuctionPriceSnapshot> priceLow,
        List<AuctionPriceSnapshot> priceHigh
) {

    public static final int MAX_ENTRIES = 1_600;

    public DownPriceSnapshot {
        Objects.requireNonNull(snapshotAt);
        priceLow = List.copyOf(priceLow);
        priceHigh = List.copyOf(priceHigh);
        if (totalCount < 0) {
            throw new IllegalArgumentException("스냅샷 전체 건수는 음수일 수 없습니다.");
        }
        int expectedSize = (int) Math.min(totalCount, MAX_ENTRIES);
        if (priceLow.size() != expectedSize || priceHigh.size() != expectedSize) {
            throw new IllegalArgumentException("가격 스냅샷 크기가 전체 건수와 일치하지 않습니다.");
        }
    }
}
