package com.tikitaka.bidwinback.auction.application.bid;

import com.tikitaka.bidwinback.auction.application.live.BidPriceCachePreempted;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * place()가 Redis 선점(tryWinRace) 이후 조건부 UPDATE는 성공했더라도, 그 뒤 보증금 예약이나
 * Bid 저장에서 실패해 트랜잭션 전체가 롤백되면 MySQL의 current_price는 원래대로 돌아가지만
 * Redis는 그대로 남는다. 이 리스너가 그 경우를 잡아 캐시를 DB 현재가로 재동기화한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidPriceCacheRevertListener {

    private final BidPriceCache bidPriceCache;
    private final AuctionRepository auctionRepository;

    // AFTER_ROLLBACK 시점엔 원래 트랜잭션이 이미 끝나있어 참여할 트랜잭션이 없으므로
    // REQUIRES_NEW로 새 트랜잭션을 열어야 한다(그냥 @Transactional은 스프링이 막는다).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void revertOnRollback(BidPriceCachePreempted event) {
        try {
            auctionRepository.findCurrentPriceById(event.auctionId())
                    .ifPresent(actualPrice -> bidPriceCache.resyncToActualPrice(
                            event.auctionId(),
                            event.price(),
                            actualPrice
                    ));
        } catch (RuntimeException exception) {
            log.warn("롤백된 입찰의 가격 캐시 재동기화에 실패했습니다. auctionId={}", event.auctionId(), exception);
        }
    }
}
