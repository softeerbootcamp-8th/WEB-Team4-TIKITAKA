package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListRow;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListSearchCondition;

import java.util.List;

public interface AuctionListQueryRepository {

    long count(AuctionListSearchCondition condition);

    List<AuctionListRow> findPage(
            AuctionListSearchCondition condition,
            long offset,
            int limit
    );
}
