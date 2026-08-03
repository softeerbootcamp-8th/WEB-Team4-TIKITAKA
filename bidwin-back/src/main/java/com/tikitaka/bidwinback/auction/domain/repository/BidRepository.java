package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.Bid;
import com.tikitaka.bidwinback.auction.domain.repository.dto.BidHistoryRow;
import com.tikitaka.bidwinback.auction.domain.repository.dto.BidSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BidRepository extends JpaRepository<Bid, Long> {

    @Query("""
            select max(bid.price)
            from Bid bid
            where bid.auction.id = :auctionId
            """)
    Long findHighestPriceByAuctionId(@Param("auctionId") long auctionId);

    @Query("""
            select max(bid.price), count(bid.id)
            from Bid bid
            where bid.auction.id = :auctionId
            """)
    BidSummary summarizeByAuctionId(@Param("auctionId") long auctionId);

    @Query("""
            select count(bid.id)
            from Bid bid
            where bid.auction.id = :auctionId
            """)
    long countByAuctionId(@Param("auctionId") long auctionId);

    @Query("""
        select bid.id,
               bidder.id,
               bidder.nickname,
               bid.price,
               bid.createdAt
        from Bid bid
        join bid.bidder bidder
        where bid.auction.id = :auctionId
        order by bid.createdAt desc, bid.id desc
        limit 10
        """)
    List<BidHistoryRow> findHistoryByAuctionId(@Param("auctionId") long auctionId);
}
