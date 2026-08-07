package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.SealedBid;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionBidSummary;
import com.tikitaka.bidwinback.auction.domain.repository.dto.BidHistoryRow;
import com.tikitaka.bidwinback.auction.domain.repository.dto.MyBidAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // 마이페이지 입찰 내역용. BidRepository.summarizeMyBidsByMemberId와 같은 모양으로,
    // 밀봉 구간에 내가 넣은 밀봉입찰의 경매별 최고가·마지막 제출 시각을 뽑는다.
    @Query("""
            select new com.tikitaka.bidwinback.auction.domain.repository.dto.MyBidAggregate(
                sealedBid.auction.id, max(sealedBid.price), max(sealedBid.submittedAt)
            )
            from SealedBid sealedBid
            where sealedBid.bidder.id = :memberId
            group by sealedBid.auction.id
            """)
    List<MyBidAggregate> summarizeMySealedBidsByMemberId(@Param("memberId") Long memberId);

    // 목록 전체(공개+밀봉) 최고가 판정용. bidCount는 이 용도에서 안 쓰지만, 이미 있는
    // AuctionBidSummary를 재사용해 DTO를 새로 늘리지 않는다.
    @Query("""
            select new com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionBidSummary(
                sealedBid.auction.id, max(sealedBid.price), count(sealedBid.id)
            )
            from SealedBid sealedBid
            where sealedBid.auction.id in :auctionIds
            group by sealedBid.auction.id
            """)
    List<AuctionBidSummary> summarizeSealedByAuctionIds(@Param("auctionIds") List<Long> auctionIds);

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
