package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionDeposit;
import com.tikitaka.bidwinback.auction.domain.enums.DepositStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AuctionDepositRepository extends JpaRepository<AuctionDeposit, Long> {

    boolean existsByMemberIdAndAuctionId(Long memberId, Long auctionId);

    // 마이페이지 보증금 내역용. auction은 제목 표시에만 필요해 fetch join으로 N+1을 피한다.
    @EntityGraph(attributePaths = "auction")
    Page<AuctionDeposit> findByMemberIdAndStatusIn(
            Long memberId,
            List<DepositStatus> statuses,
            Pageable pageable
    );

    // 정산 재시도도 기존 행을 잠근 뒤 상태를 판별할 수 있도록 상태 조건 없이 조회한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuctionDeposit> findByAuctionIdAndMemberId(Long auctionId, Long memberId);

    // 증액 요청도 보증금 행을 먼저 잠그게 해 동일 요청을 직렬화하고 회원 행과의 락 순서를 통일한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuctionDeposit> findByAuctionIdAndMemberIdAndStatus(
            Long auctionId,
            Long memberId,
            DepositStatus status
    );

    // 마감 배치가 선점한 경매에서 낙찰자를 제외한 HELD 보증금을 잠근다. 이후 회원 행은
    // 이 조회 순서대로 갱신해 여러 경매가 동시에 마감돼도 회원 행 잠금 순서를 고정한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select deposit
            from AuctionDeposit deposit
            where deposit.auction.id in :auctionIds
              and deposit.status = :status
              and exists (
                  select trade.id
                  from AuctionTrade trade
                  where trade.auction.id = deposit.auction.id
                    and trade.buyer.id <> deposit.member.id
              )
            order by deposit.member.id, deposit.id
            """)
    List<AuctionDeposit> findLosingDepositsForUpdate(
            @Param("auctionIds") List<Long> auctionIds,
            @Param("status") DepositStatus status
    );

    // HELD이고 예약 금액이 기대치와 같을 때만 다음 상태로 전이해, 이중 정산과 예약금 변동을 원자적으로 막는다.
    // Native UPDATE는 @LastModifiedDate가 적용되지 않아 last_modified_at을 직접 갱신한다.
    @Modifying
    @Query(value = """
            UPDATE auction_deposit
            SET status = :status,
                last_modified_at = SYSDATE(6)
            WHERE id = :depositId
              AND status = 'HELD'
              AND reserved_amount = :expectedAmount
            """, nativeQuery = true)
    int settleIfHeldWithAmount(
            @Param("depositId") Long depositId,
            @Param("status") String status,
            @Param("expectedAmount") long expectedAmount
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
