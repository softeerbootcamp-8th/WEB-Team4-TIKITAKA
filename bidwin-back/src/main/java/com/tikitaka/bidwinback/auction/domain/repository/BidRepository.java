package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.Bid;
import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;
import com.tikitaka.bidwinback.auction.domain.repository.dto.BidHistoryRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {

    @Query("""
            select bid
            from Bid bid
            join fetch bid.bidder
            where bid.auction.id = :auctionId
              and bid.status = :status
            order by bid.price desc, bid.createdAt asc, bid.id asc
            limit 1
            """)
    Optional<Bid> findWinnerByAuctionIdAndStatus(
            @Param("auctionId") long auctionId,
            @Param("status") BidStatus status
    );

    @Query("""
            select max(bid.price)
            from Bid bid
            where bid.auction.id = :auctionId
            """)
    Long findHighestPriceByAuctionId(@Param("auctionId") long auctionId);

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
