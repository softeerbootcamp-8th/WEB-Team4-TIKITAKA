package com.tikitaka.bidwinback.global.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

// TEMP: AuctionLiveStateService가 dev에 존재하지 않아(PR #109 분할 누락으로 보임) 컴파일이
// 깨져 있었다. 실제로 안 쓰이던 필드(아래 TODO)라 임시로 제거해 로컬 빌드만 풀어둔 상태이고,
// dev에서 이 클래스가 정식으로 추가되면 이 주석과 함께 원래대로 되돌려야 한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class SseHeartbeatScheduler {

    private static final SseChannel HEARTBEAT_CHANNEL =
            new SseChannel("system", "heartbeat");

    private final SseHub sseHub;
    private final AtomicLong heartbeatVersion = new AtomicLong();

    @Scheduled(fixedDelayString = "${app.sse.heartbeat-interval-ms:15000}")
    public void sendHeartbeat() {
        if (!sseHub.hasConnections()) {
            return;
        }

        try {
            //TODO long serverTime = stateService.getDatabaseTimeMillis();
            long serverTime = 1;
            sseHub.broadcast(new SseMessage<>(
                    HEARTBEAT_CHANNEL,
                    "heartbeat",
                    heartbeatVersion.incrementAndGet(),
                    serverTime
            ));
        } catch (RuntimeException exception) {
            log.warn("SSE heartbeat를 전송하지 못했습니다.", exception);
        }
    }
}
