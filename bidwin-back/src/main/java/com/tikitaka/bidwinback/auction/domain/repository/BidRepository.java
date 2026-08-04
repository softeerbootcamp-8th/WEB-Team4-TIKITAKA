package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.Bid;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionBidSummary;
import com.tikitaka.bidwinback.auction.domain.repository.dto.BidSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BidRepository extends JpaRepository<Bid, Long> {

    @Query("""
            select max(bid.price), count(bid.id)
            from Bid bid
            where bid.auction.id = :auctionId
            """)
    BidSummary summarizeByAuctionId(@Param("auctionId") long auctionId);

    // 목록 조회용 일괄 집계. asOf 이후에 들어온 입찰은 스냅샷 이후 값이라 제외한다
    // (페이지를 넘기는 동안 상향 경매 순위가 흔들리지 않도록).
    @Query("""
            select new com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionBidSummary(
                bid.auction.id, max(bid.price), count(bid.id)
            )
            from Bid bid
            where bid.auction.id in :auctionIds
              and bid.createdAt <= :asOf
            group by bid.auction.id
            """)
    List<AuctionBidSummary> summarizeByAuctionIds(
            @Param("auctionIds") List<Long> auctionIds,
            @Param("asOf") LocalDateTime asOf
    );
}
