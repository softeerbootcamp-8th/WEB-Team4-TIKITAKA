package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionDeposit;
import com.tikitaka.bidwinback.auction.domain.enums.DepositStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionDepositRepository extends JpaRepository<AuctionDeposit, Long> {

    boolean existsByMemberIdAndAuctionIdAndStatusAndReservedAmountGreaterThan(
            Long memberId,
            Long auctionId,
            DepositStatus status,
            long minimumAmount
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE auction_deposit
            SET status = 'USED',
                last_modified_at = CURRENT_TIMESTAMP(6)
            WHERE member_id = :memberId
              AND auction_id = :auctionId
              AND status = 'HELD'
              AND reserved_amount > 0
            """, nativeQuery = true)
    int useHeldDeposit(
            @Param("memberId") Long memberId,
            @Param("auctionId") Long auctionId
    );
}
