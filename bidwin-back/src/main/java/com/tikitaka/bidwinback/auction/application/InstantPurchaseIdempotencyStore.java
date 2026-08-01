package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;

import java.util.Optional;

public interface InstantPurchaseIdempotencyStore {

    Optional<SavedPurchase> claim(
            String idempotencyKey,
            Long buyerId,
            Long auctionId
    );

    void complete(
            String idempotencyKey,
            AuctionTrade trade,
            long finalPrice
    );

    record SavedPurchase(Long tradeId, long finalPrice) {
    }
}
