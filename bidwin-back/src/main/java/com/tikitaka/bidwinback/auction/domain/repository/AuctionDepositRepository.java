package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionDeposit;
import com.tikitaka.bidwinback.auction.domain.enums.DepositStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuctionDepositRepository extends JpaRepository<AuctionDeposit, Long> {

    boolean existsByMemberIdAndAuctionId(Long memberId, Long auctionId);

    // 증액·반환·몰수가 모두 보증금 행을 먼저 잠그게 해 동일 요청을 직렬화하고 회원 행과의 락 순서를 통일한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuctionDeposit> findByAuctionIdAndMemberIdAndStatus(
            Long auctionId,
            Long memberId,
            DepositStatus status
    );

    // 예약 금액을 기대한 현재값에서 새 값으로만 올려, 동시 증액 시 갱신 유실을 막는다.
    @Modifying
    @Query(value = """
            UPDATE auction_deposit
            SET reserved_amount = :newAmount,
                last_modified_at = SYSDATE(6)
            WHERE id = :depositId
              AND status = 'HELD'
              AND reserved_amount = :expectedCurrent
            """, nativeQuery = true)
    int increaseReservedIfHeld(
            @Param("depositId") Long depositId,
            @Param("expectedCurrent") long expectedCurrent,
            @Param("newAmount") long newAmount
    );
}
