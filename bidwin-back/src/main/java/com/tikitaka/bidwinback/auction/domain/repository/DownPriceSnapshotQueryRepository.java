package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DownPriceSnapshotQueryRepository {

    Optional<LocalDateTime> findLatestSnapshotAtNotAfter(LocalDateTime asOf);

    long count(LocalDateTime snapshotAt, String keyword);

    List<AuctionPriceSnapshot> findPage(
            LocalDateTime snapshotAt,
            AuctionSort sort,
            String keyword,
            long offset,
            int limit
    );
}
