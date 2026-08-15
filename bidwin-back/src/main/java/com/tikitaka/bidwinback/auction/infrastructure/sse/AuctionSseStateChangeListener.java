package com.tikitaka.bidwinback.auction.infrastructure.sse;

import com.tikitaka.bidwinback.auction.application.live.AuctionBidHistoryCache;
import com.tikitaka.bidwinback.auction.application.live.AuctionLiveStateCache;
import com.tikitaka.bidwinback.auction.application.live.AuctionLiveStateService;
import com.tikitaka.bidwinback.auction.application.live.AuctionStateChanged;
import com.tikitaka.bidwinback.global.sse.RedisSseEventBus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 커밋된 경매 변경을 최신 snapshot으로 읽어 Redis에 발행한다. */
@Slf4j
@Component
public class AuctionSseStateChangeListener {

    private final AuctionLiveStateService stateService;
    private final AuctionLiveStateCache stateCache;
    private final AuctionBidHistoryCache bidHistoryCache;
    private final RedisSseEventBus eventBus;

    public AuctionSseStateChangeListener(
            AuctionLiveStateService stateService,
            AuctionLiveStateCache stateCache,
            AuctionBidHistoryCache bidHistoryCache,
            RedisSseEventBus eventBus
    ) {
        this.stateService = stateService;
        this.stateCache = stateCache;
        this.bidHistoryCache = bidHistoryCache;
        this.eventBus = eventBus;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = false
    )
    public void publishCommittedState(AuctionStateChanged event) {
        long auctionId = event.auctionId();
        // 구독자가 없더라도 다음 연결이 커밋 전 snapshot을 받지 않게 먼저 무효화한다.
        stateCache.invalidate(auctionId);
        bidHistoryCache.invalidate(auctionId);
        try {
            // 모든 인스턴스가 같은 절대 상태를 받도록 커밋된 DB snapshot을 Redis에 싣는다.
            eventBus.publish(AuctionSseMessages.state(stateService.getState(auctionId)));
        } catch (RuntimeException exception) {
            // 다음 변경이나 재연결 snapshot으로 수렴할 수 있으므로 비즈니스 결과와 격리한다.
            log.warn("커밋된 경매 상태를 Redis로 발행하지 못했습니다. auctionId={}", auctionId, exception);
        }
    }
}
