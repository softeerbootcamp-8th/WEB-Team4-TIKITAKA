package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.domain.entity.BuyNowRequestLog;
import com.tikitaka.bidwinback.auction.domain.repository.BuyNowIdempotencyStore;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaBuyNowIdempotencyStore implements BuyNowIdempotencyStore {

    private final EntityManager entityManager;

    @Override
    public Optional<BuyNowRequestLog> findByKey(String idempotencyKey) {
        return entityManager.createQuery("""
                        select request
                        from BuyNowRequestLog request
                        left join fetch request.trade
                        where request.idempotencyKey = :idempotencyKey
                        """, BuyNowRequestLog.class)
                .setParameter("idempotencyKey", idempotencyKey)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    public void saveAndFlush(BuyNowRequestLog requestLog) {
        entityManager.persist(requestLog);
        entityManager.flush();
    }
}
