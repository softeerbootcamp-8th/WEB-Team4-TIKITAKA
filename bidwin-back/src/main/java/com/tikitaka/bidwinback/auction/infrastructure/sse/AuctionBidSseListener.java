package com.tikitaka.bidwinback.auction.infrastructure.sse;

import com.tikitaka.bidwinback.auction.application.BidHistoryService;
import com.tikitaka.bidwinback.auction.application.live.AuctionBidCreated;
import com.tikitaka.bidwinback.auction.application.live.AuctionBidHistoryRevealed;
import com.tikitaka.bidwinback.global.sse.RedisSseEventBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 커밋된 공개 입찰과 마감 시점의 입찰 내역 snapshot을 Redis에 발행한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionBidSseListener {

    private final BidHistoryService bidHistoryService;
    private final RedisSseEventBus eventBus;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishBid(AuctionBidCreated event) {
        try {
            eventBus.publish(AuctionSseMessages.bidCreated(
                    event.auctionId(),
                    event.bidId(),
                    event.bid()
            ));
        } catch (RuntimeException exception) {
            log.warn(
                    "커밋된 입찰을 SSE로 발행하지 못했습니다. auctionId={}, bidId={}",
                    event.auctionId(),
                    event.bidId(),
                    exception
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishRevealedHistory(AuctionBidHistoryRevealed event) {
        try {
            eventBus.publish(AuctionSseMessages.bidHistorySnapshot(
                    event.auctionId(),
                    event.revision(),
                    bidHistoryService.getBidHistory(event.auctionId())
            ));
        } catch (RuntimeException exception) {
            log.warn(
                    "공개된 입찰 내역을 SSE로 발행하지 못했습니다. auctionId={}",
                    event.auctionId(),
                    exception
            );
        }
    }
}
