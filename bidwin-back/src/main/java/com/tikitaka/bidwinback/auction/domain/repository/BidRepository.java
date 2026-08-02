package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.Bid;
import com.tikitaka.bidwinback.auction.domain.repository.dto.BidSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BidRepository extends JpaRepository<Bid, Long> {

    @Query("""
            select max(bid.price), count(bid.id)
            from Bid bid
            where bid.auction.id = :auctionId
            """)
    BidSummary summarizeByAuctionId(@Param("auctionId") long auctionId);
}
