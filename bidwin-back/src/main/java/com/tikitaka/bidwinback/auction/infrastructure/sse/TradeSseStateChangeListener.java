package com.tikitaka.bidwinback.auction.infrastructure.sse;

import com.tikitaka.bidwinback.auction.application.live.TradeStatusChanged;
import com.tikitaka.bidwinback.global.sse.RedisSseEventBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 서비스가 만든 거래 상태 snapshot을 커밋 뒤 Redis에 발행한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeSseStateChangeListener {

    private final RedisSseEventBus eventBus;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = false
    )
    public void publishCommittedState(TradeStatusChanged event) {
        long tradeId = event.state().tradeId();
        try {
            eventBus.publish(TradeSseMessages.state(event.state()));
        } catch (RuntimeException exception) {
            // 다음 변경이나 재연결 snapshot으로 수렴할 수 있으므로 비즈니스 결과와 격리한다.
            log.warn("커밋된 거래 상태를 Redis로 발행하지 못했습니다. tradeId={}", tradeId, exception);
        }
    }
}
