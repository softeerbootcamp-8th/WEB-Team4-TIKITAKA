package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    // 단일 조건부 UPDATE로 입찰을 직렬화하고 최소 호가 검증과 현재가 변경을 원자적으로 처리한다.
    // current_price가 없는 기존 경매만 Bid 최고가, 입찰도 없으면 시작가를 기준으로 한다.
    @Modifying
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "3000"))
    @Query(value = """
            UPDATE auction
            SET current_price = :price,
                status = 'BID_ONGOING',
                last_modified_at = SYSDATE(6) -- Native UPDATE는 @LastModifiedDate가 적용되지 않아 직접 갱신한다.
            WHERE id = :auctionId
              AND auction_type = 'UP'
              AND status IN ('OPEN', 'BID_ONGOING')
              AND completed_at IS NULL
              AND ended_at > DATE_ADD(SYSDATE(6), INTERVAL 5 MINUTE)
              AND seller_id <> :bidderId
              AND COALESCE(
                    current_price,
                    (
                        SELECT MAX(bid.price)
                        FROM bid
                        WHERE bid.auction_id = auction.id
                    ),
                    start_price
              ) <= :price - :bidUnit
            """, nativeQuery = true)
    int updateCurrentPriceForBid(
            @Param("auctionId") Long auctionId,
            @Param("bidderId") Long bidderId,
            @Param("price") long price,
            @Param("bidUnit") long bidUnit
    );

    // 밀봉 구간에는 공개 현재가를 바꾸지 않고 경매 상태만 변경한다.
    @Modifying
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "3000"))
    @Query(value = """
            UPDATE auction
            SET status = 'BID_ONGOING',
                last_modified_at = SYSDATE(6)
            WHERE id = :auctionId
              AND auction_type = 'UP'
              AND status IN ('OPEN', 'BID_ONGOING')
              AND completed_at IS NULL
              AND ended_at > SYSDATE(6)
              AND ended_at <= DATE_ADD(SYSDATE(6), INTERVAL 5 MINUTE)
              AND seller_id <> :bidderId
              AND COALESCE(
                    current_price,
                    (
                        SELECT MAX(bid.price)
                        FROM bid
                        WHERE bid.auction_id = auction.id
                    ),
                    start_price
              ) <= :price - :bidUnit
            """, nativeQuery = true)
    int tryUpdateAuctionForSealedBid(
            @Param("auctionId") Long auctionId,
            @Param("bidderId") Long bidderId,
            @Param("price") long price,
            @Param("bidUnit") long bidUnit
    );

    // 정산 시 진행 중인 입찰과 중복 정산을 동일 경매 행 기준으로 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select auction
            from Auction auction
            join fetch auction.seller
            where auction.id = :auctionId
            """)
    Optional<Auction> findByIdForUpdate(@Param("auctionId") long auctionId);

    @Query("""
            select auction
            from Auction auction
            join fetch auction.seller
            where auction.id = :auctionId
            """)
    Optional<Auction> findWithSellerById(@Param("auctionId") Long auctionId);

    // 경매 상태·마감·판매자를 DB에서 다시 검사하고 한 요청만 완료 처리한다.
    @Modifying
    @Query(value = """
            UPDATE auction
            SET status = 'COMPLETED',
                completed_at = :completedAt,
                last_modified_at = :completedAt
            WHERE id = :auctionId
              AND status = 'OPEN'
              AND completed_at IS NULL
              AND ended_at > SYSDATE(6)
              AND seller_id <> :buyerId
            """, nativeQuery = true)
    int completeForBuyNow(
            @Param("auctionId") Long auctionId,
            @Param("buyerId") Long buyerId,
            @Param("completedAt") LocalDateTime completedAt
    );

    // 요구사항: 낙찰 시각과 하향 경매 가격 계산은 DB가 기록한 동일 시각을 사용한다.
    @Query(value = """
            SELECT completed_at
            FROM auction
            WHERE id = :auctionId
            """, nativeQuery = true)
    Optional<LocalDateTime> findCompletedAt(@Param("auctionId") Long auctionId);

    @EntityGraph(attributePaths = "seller")
    @Query("select auction from Auction auction where auction.id = :auctionId")
    Optional<Auction> findDetailById(@Param("auctionId") long auctionId);

    // 하락 경매의 계산 기준이 애플리케이션 서버마다 달라지지 않도록 DB 시각을 사용한다.
    @Query(value = "select current_timestamp(6)", nativeQuery = true)
    LocalDateTime currentDatabaseTime();
}
