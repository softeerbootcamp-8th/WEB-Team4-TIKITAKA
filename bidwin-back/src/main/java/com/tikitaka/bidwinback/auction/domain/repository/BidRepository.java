package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.Bid;
import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionBidSummary;
import com.tikitaka.bidwinback.auction.domain.repository.dto.BidHistoryRow;
import com.tikitaka.bidwinback.auction.domain.repository.dto.MyBidAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {

    @Query("""
            select bid
            from Bid bid
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

    // MySQL이 member부터 훑지 않도록 입찰 이력 인덱스 순서로 최근 10건을 먼저 읽는다.
    @Query(value = """
            SELECT STRAIGHT_JOIN
                   bid.id,
                   member.id,
                   member.nickname,
                   bid.price,
                   bid.created_at
            FROM bid
            JOIN member ON member.id = bid.bidder_id
            WHERE bid.auction_id = :auctionId
            ORDER BY bid.created_at DESC, bid.id DESC
            LIMIT 10
            """, nativeQuery = true)
    List<BidHistoryRow> findHistoryByAuctionId(@Param("auctionId") long auctionId);

    // 일반·밀봉입찰을 모두 포함하되 같은 경매 참여는 한 번만 센다.
    @Query(value = """
            select count(*)
            from (
                select bid.auction_id
                from bid
                where bid.bidder_id = :memberId
                union
                select sealed_bid.auction_id
                from sealed_bid
                where sealed_bid.bidder_id = :memberId
            ) participated_auction
            """, nativeQuery = true)
    long countDistinctAuctionByBidderId(@Param("memberId") long memberId);
           
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

    // 마이페이지 입찰 내역용. 내가 입찰한 경매별로 내 최고가·마지막 입찰 시각만 뽑는다.
    @Query("""
            select new com.tikitaka.bidwinback.auction.domain.repository.dto.MyBidAggregate(
                bid.auction.id, max(bid.price), max(bid.createdAt)
            )
            from Bid bid
            where bid.bidder.id = :memberId
            group by bid.auction.id
            """)
    List<MyBidAggregate> summarizeMyBidsByMemberId(@Param("memberId") Long memberId);

    // 실시간 상태 스냅샷용 일괄 집계. 목록 SSE 구독 때 경매마다 개별 조회하던 걸 한 번에 모은다.
    // 상세 조회와 값을 맞추기 위해 asOf·상태 필터 없이 현재 시점의 최고가·입찰 수를 그대로 센다.
    @Query("""
            select bid.auction.id, max(bid.price), count(bid.id)
            from Bid bid
            where bid.auction.id in :auctionIds
            group by bid.auction.id
            """)
    List<AuctionBidSummary> summarizeAllByAuctionIds(
            @Param("auctionIds") Collection<Long> auctionIds
    );
}
