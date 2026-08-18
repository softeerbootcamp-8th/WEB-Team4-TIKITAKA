package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;

import java.time.LocalDateTime;
import java.util.List;

public record DownPriceSnapshotPage(
        LocalDateTime generationAt,
        List<AuctionPriceSnapshot> entries,
        int totalCount
) {

    public DownPriceSnapshotPage {
        entries = List.copyOf(entries);
        if (totalCount < entries.size()
                || totalCount > DownPriceSnapshot.MAX_ENTRIES_PER_SORT) {
            throw new IllegalArgumentException("스냅샷 전체 건수가 유효하지 않습니다.");
        }
    }
}
