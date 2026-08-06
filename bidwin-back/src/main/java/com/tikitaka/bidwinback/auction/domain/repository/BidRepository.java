package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.Bid;
import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;
import com.tikitaka.bidwinback.auction.domain.repository.dto.BidHistoryRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BidRepository extends JpaRepository<Bid, Long> {

    @Query("""
            select max(bid.price)
            from Bid bid
            where bid.auction.id = :auctionId
              and bid.status = :status
            """)
    Long findHighestPriceByAuctionIdAndStatus(
            @Param("auctionId") long auctionId,
            @Param("status") BidStatus status
    );

    @Query("""
            select count(bid.id)
            from Bid bid
            where bid.auction.id = :auctionId
              and (:revealSealed = true or bid.status <> :sealedStatus)
            """)
    long countVisibleByAuctionId(
            @Param("auctionId") long auctionId,
            @Param("sealedStatus") BidStatus sealedStatus,
            @Param("revealSealed") boolean revealSealed
    );

    @Query("""
        select bid.id,
               bidder.id,
               bidder.nickname,
               bid.price,
               bid.createdAt
        from Bid bid
        join bid.bidder bidder
        where bid.auction.id = :auctionId
          and (:revealSealed = true or bid.status <> :sealedStatus)
        order by bid.createdAt desc, bid.id desc
        limit 10
        """)
    List<BidHistoryRow> findVisibleHistoryByAuctionId(
            @Param("auctionId") long auctionId,
            @Param("sealedStatus") BidStatus sealedStatus,
            @Param("revealSealed") boolean revealSealed
    );
}
