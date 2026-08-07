package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.SealedBid;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionSealedBidCount;
import com.tikitaka.bidwinback.auction.domain.repository.dto.BidHistoryRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SealedBidRepository extends JpaRepository<SealedBid, Long> {

    @Query("""
            select max(sealedBid.price)
            from SealedBid sealedBid
            where sealedBid.auction.id = :auctionId
            """)
    Long findHighestPriceByAuctionId(@Param("auctionId") long auctionId);

    long countByAuctionId(long auctionId);

    // 실시간 상태 스냅샷용 일괄 집계. 공개된 경매의 밀봉 입찰 수를 목록 SSE에서 한 번에 모은다.
    @Query("""
            select sealedBid.auction.id, count(sealedBid.id)
            from SealedBid sealedBid
            where sealedBid.auction.id in :auctionIds
            group by sealedBid.auction.id
            """)
    List<AuctionSealedBidCount> countByAuctionIds(
            @Param("auctionIds") Collection<Long> auctionIds
    );

    @Query("""
            select sealedBid.id,
                   bidder.id,
                   bidder.nickname,
                   sealedBid.price,
                   sealedBid.submittedAt
            from SealedBid sealedBid
            join sealedBid.bidder bidder
            where sealedBid.auction.id = :auctionId
            order by sealedBid.submittedAt desc, sealedBid.id desc
            limit 10
            """)
    List<BidHistoryRow> findHistoryByAuctionId(@Param("auctionId") long auctionId);

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
