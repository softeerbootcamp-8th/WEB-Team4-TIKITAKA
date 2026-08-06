package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.SealedBid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SealedBidRepository extends JpaRepository<SealedBid, Long> {

    @Query("""
            select max(sealedBid.price)
            from SealedBid sealedBid
            where sealedBid.auction.id = :auctionId
            """)
    Long findHighestPriceByAuctionId(@Param("auctionId") long auctionId);

    @Query("""
            select sealedBid
            from SealedBid sealedBid
            join fetch sealedBid.bidder
            where sealedBid.auction.id = :auctionId
            order by sealedBid.price desc, sealedBid.submittedAt asc, sealedBid.id asc
            limit 1
            """)
    Optional<SealedBid> findWinnerByAuctionId(@Param("auctionId") long auctionId);
}
