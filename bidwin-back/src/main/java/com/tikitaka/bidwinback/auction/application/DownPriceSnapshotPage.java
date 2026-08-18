package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;

import java.time.LocalDateTime;
import java.util.List;

public record DownPriceSnapshotPage(
        LocalDateTime generationAt,
        List<AuctionPriceSnapshot> entries
) {

    public DownPriceSnapshotPage {
        entries = List.copyOf(entries);
    }
}
