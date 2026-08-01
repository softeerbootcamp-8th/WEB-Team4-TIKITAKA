package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.InstantPurchaseIdempotencyStore;
import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.entity.InstantPurchaseRequest;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.IDEMPOTENCY_KEY_REUSED;

@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class JpaInstantPurchaseIdempotencyStore
        implements InstantPurchaseIdempotencyStore {

    private final EntityManager entityManager;

    @Override
    public Optional<SavedPurchase> claim(
            String idempotencyKey,
            Long buyerId,
            Long auctionId
    ) {
        // UNIQUE 키의 충돌까지 DB가 직렬화하므로 서버가 여러 대여도 같은 요청은 한 번만 실행된다.
        entityManager.createNativeQuery("""
                        INSERT INTO instant_purchase_request (
                            idempotency_key,
                            buyer_id,
                            auction_id
                        )
                        VALUES (:idempotencyKey, :buyerId, :auctionId)
                        ON DUPLICATE KEY UPDATE
                            idempotency_key = instant_purchase_request.idempotency_key
                        """)
                .setParameter("idempotencyKey", idempotencyKey)
                .setParameter("buyerId", buyerId)
                .setParameter("auctionId", auctionId)
                .executeUpdate();

        InstantPurchaseRequest request = findByKeyForUpdate(idempotencyKey);
        if (!request.belongsTo(buyerId, auctionId)) {
            throw new AuctionException(IDEMPOTENCY_KEY_REUSED);
        }
        if (!request.isCompleted()) {
            return Optional.empty();
        }
        return Optional.of(new SavedPurchase(
                request.getTrade().getId(),
                request.getFinalPrice()
        ));
    }

    @Override
    public void complete(
            String idempotencyKey,
            AuctionTrade trade,
            long finalPrice
    ) {
        findByKeyForUpdate(idempotencyKey).complete(trade, finalPrice);
    }

    private InstantPurchaseRequest findByKeyForUpdate(String idempotencyKey) {
        return entityManager.createQuery("""
                        SELECT request
                        FROM InstantPurchaseRequest request
                        WHERE request.idempotencyKey = :idempotencyKey
                        """, InstantPurchaseRequest.class)
                .setParameter("idempotencyKey", idempotencyKey)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getSingleResult();
    }
}
