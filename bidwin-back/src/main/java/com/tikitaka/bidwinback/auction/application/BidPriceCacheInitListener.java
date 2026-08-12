package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.application.live.AuctionCreated;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 상향 경매가 커밋되면 입찰가 캐시를 시작가로 미리 채워, 캐시 미존재로 인한 오탐(0으로 취급)을 막는다. */
@Component
@RequiredArgsConstructor
public class BidPriceCacheInitListener {

    private final BidPriceCache bidPriceCache;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = false
    )
    public void initializeCache(AuctionCreated event) {
        bidPriceCache.initialize(event.auctionId(), event.startPrice(), event.endedAt());
    }
}
