package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListRow;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListSearchCondition;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceCursor;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import com.tikitaka.bidwinback.auction.domain.repository.dto.DownAuctionPriceCandidate;

import java.time.LocalDateTime;
import java.util.List;

public interface AuctionListQueryRepository {

    long count(AuctionListSearchCondition condition);

    List<AuctionListRow> findPage(
            AuctionListSearchCondition condition,
            long offset,
            int limit
    );

    List<AuctionPriceSnapshot> findUpPriceSnapshots(
            AuctionListSearchCondition condition,
            int limit
    );

    List<DownAuctionPriceCandidate> findDownPriceCandidates(
            AuctionListSearchCondition condition,
            AuctionPriceCursor cursor,
            int limit
    );

    List<DownAuctionPriceCandidate> findRemainingDownPriceCandidatesAtBound(
            AuctionListSearchCondition condition,
            AuctionPriceCursor cursor
    );

    List<AuctionListRow> findRowsByPriceSnapshots(
            List<AuctionPriceSnapshot> snapshots,
            LocalDateTime asOf
    );

    List<AuctionListRow> findDownRowsByPriceSnapshots(
            List<AuctionPriceSnapshot> snapshots,
            LocalDateTime asOf
    );
}
