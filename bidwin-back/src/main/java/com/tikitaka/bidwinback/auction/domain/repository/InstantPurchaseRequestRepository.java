package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.InstantPurchaseRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InstantPurchaseRequestRepository
        extends JpaRepository<InstantPurchaseRequest, Long> {

    // UNIQUE 충돌을 예외 대신 재요청 흐름으로 바꿔 동일 요청을 DB에서 직렬화한다.
    @Modifying
    @Query(value = """
            INSERT INTO instant_purchase_request (
                idempotency_key,
                buyer_id,
                auction_id
            )
            VALUES (:idempotencyKey, :buyerId, :auctionId)
            ON DUPLICATE KEY UPDATE
                idempotency_key = instant_purchase_request.idempotency_key
            """, nativeQuery = true)
    void insertOrKeep(
            @Param("idempotencyKey") String idempotencyKey,
            @Param("buyerId") Long buyerId,
            @Param("auctionId") Long auctionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT request
            FROM InstantPurchaseRequest request
            LEFT JOIN FETCH request.trade
            WHERE request.idempotencyKey = :idempotencyKey
            """)
    Optional<InstantPurchaseRequest> findByIdempotencyKeyForUpdate(
            @Param("idempotencyKey") String idempotencyKey
    );
}
