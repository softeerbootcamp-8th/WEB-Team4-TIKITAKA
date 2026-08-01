package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    @Query(value = "SELECT CURRENT_TIMESTAMP(6)", nativeQuery = true)
    LocalDateTime findDatabaseNow();

    // 네이티브 UPDATE 뒤 관리 중인 Auction이 이전 상태를 유지하지 않도록 영속성 컨텍스트를 비운다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE auction
            SET status = 'COMPLETED'
            WHERE id = :auctionId
              AND status IN ('OPEN', 'BID_ONGOING')
              AND ended_at > CURRENT_TIMESTAMP(6)
            """, nativeQuery = true)
    int completeForInstantPurchase(@Param("auctionId") Long auctionId);
}
