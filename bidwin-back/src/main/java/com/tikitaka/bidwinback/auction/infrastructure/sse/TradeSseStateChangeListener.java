package com.tikitaka.bidwinback.auction.infrastructure.sse;

import com.tikitaka.bidwinback.auction.application.live.TradeLiveStateService;
import com.tikitaka.bidwinback.auction.application.live.TradeStatusChanged;
import com.tikitaka.bidwinback.global.sse.SseHub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 커밋된 거래 상태 변경을 최신 snapshot으로 읽어 거래 SSE 연결에 전달한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeSseStateChangeListener {

    private final TradeLiveStateService stateService;
    private final SseHub sseHub;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = false
    )
    public void publishCommittedState(TradeStatusChanged event) {
        long tradeId = event.tradeId();
        if (!sseHub.hasSubscribers(TradeSseMessages.channel(tradeId))) {
            return;
        }

        long preparationStartedAtNanos = System.nanoTime();
        try {
            // 상태는 별도 읽기 트랜잭션에서 조회하고, socket write는 SseConnection이 담당한다.
            sseHub.publish(
                    TradeSseMessages.state(stateService.getState(tradeId)),
                    preparationStartedAtNanos
            );
        } catch (RuntimeException exception) {
            // 다음 변경이나 재연결 snapshot으로 수렴할 수 있으므로 비즈니스 결과와 격리한다.
            log.warn("커밋된 거래 상태를 SSE로 발행하지 못했습니다. tradeId={}", tradeId, exception);
        }
    }
}
