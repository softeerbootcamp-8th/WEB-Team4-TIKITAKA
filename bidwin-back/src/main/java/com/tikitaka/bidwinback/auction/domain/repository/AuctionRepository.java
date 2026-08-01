package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    @Query(value = """
            SELECT auction.seller_id AS sellerId,
                   auction.auction_type AS auctionType,
                   auction.status AS status,
                   auction.start_price AS startPrice,
                   auction.created_at AS startedAt,
                   auction.ended_at AS endedAt,
                   up_auction.buy_now_price AS buyNowPrice,
                   down_auction.minimum_price AS minimumPrice,
                   down_auction.drop_price AS dropPrice,
                   down_auction.price_drop_interval AS priceDropInterval,
                   CURRENT_TIMESTAMP(6) AS databaseNow
            FROM auction
            LEFT JOIN up_auction
                   ON up_auction.auction_id = auction.id
            LEFT JOIN down_auction
                   ON down_auction.auction_id = auction.id
            WHERE auction.id = :auctionId
            """, nativeQuery = true)
    Optional<InstantPurchaseTarget> findInstantPurchaseTarget(
            @Param("auctionId") Long auctionId
    );

    // 마감 검증과 상태 변경 사이의 경합을 없애기 위해 DB 시각까지 WHERE 절에서 판정한다.
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE auction
            SET status = 'COMPLETED'
            WHERE id = :auctionId
              AND status IN ('OPEN', 'BID_ONGOING')
              AND ended_at > CURRENT_TIMESTAMP(6)
            """, nativeQuery = true)
    int completeForInstantPurchase(@Param("auctionId") Long auctionId);

    interface InstantPurchaseTarget {
        Long getSellerId();
        String getAuctionType();
        String getStatus();
        long getStartPrice();
        LocalDateTime getStartedAt();
        LocalDateTime getEndedAt();
        Long getBuyNowPrice();
        Long getMinimumPrice();
        Long getDropPrice();
        Long getPriceDropInterval();
        LocalDateTime getDatabaseNow();
    }
}
