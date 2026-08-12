package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.application.live.OpenBidAccepted;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 공개입찰 트랜잭션이 커밋된 뒤에만 해당 가격을 캐시에 반영한다. */
@Component
@RequiredArgsConstructor
public class BidPriceCacheUpdateListener {

    private final BidPriceCache bidPriceCache;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = false
    )
    public void updateCache(OpenBidAccepted event) {
        bidPriceCache.updateCommittedPrice(event.auctionId(), event.price(), event.endedAt());
    }
}
