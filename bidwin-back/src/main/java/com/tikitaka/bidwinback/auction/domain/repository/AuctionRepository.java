package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    @Query("""
            select auction
            from Auction auction
            join fetch auction.seller
            where auction.id = :auctionId
            """)
    Optional<Auction> findWithSellerById(@Param("auctionId") Long auctionId);

    // 요구사항: 구매 자격과 경매 상태를 DB에서 다시 검사하고 한 요청만 완료 처리한다.
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE Auction auction
            SET status = 'COMPLETED',
                completed_at = CURRENT_TIMESTAMP(6),
                last_modified_at = CURRENT_TIMESTAMP(6)
            WHERE auction.id = :auctionId
              AND auction.status = 'OPEN'
              AND auction.completed_at IS NULL
              AND auction.ended_at > CURRENT_TIMESTAMP(6)
              AND auction.seller_id <> :buyerId
              AND EXISTS (
                  SELECT 1
                  FROM Member member
                  WHERE member.id = :buyerId
                    AND member.status = 'ACTIVE'
              )
              AND EXISTS (
                  SELECT 1
                  FROM auction_deposit deposit
                  WHERE deposit.auction_id = auction.id
                    AND deposit.member_id = :buyerId
                    AND deposit.status = 'HELD'
                    AND deposit.reserved_amount > 0
              )
            """, nativeQuery = true)
    int completeForBuyNow(
            @Param("auctionId") Long auctionId,
            @Param("buyerId") Long buyerId
    );

    // 요구사항: 낙찰 시각과 하향 경매 가격 계산은 DB가 기록한 동일 시각을 사용한다.
    @Query(value = """
            SELECT completed_at
            FROM Auction
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
