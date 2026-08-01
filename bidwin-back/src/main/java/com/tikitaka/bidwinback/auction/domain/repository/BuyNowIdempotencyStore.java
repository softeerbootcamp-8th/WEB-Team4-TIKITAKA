package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.BuyNowRequestLog;

import java.util.Optional;

public interface BuyNowIdempotencyStore {

    Optional<BuyNowRequestLog> findByKey(String idempotencyKey);

    void saveAndFlush(BuyNowRequestLog requestLog);
}
