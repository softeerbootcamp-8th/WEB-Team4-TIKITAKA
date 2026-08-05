package com.tikitaka.bidwinback.global.sse;

import com.tikitaka.bidwinback.auction.application.live.AuctionLiveStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseHeartbeatScheduler {

    private static final SseChannel HEARTBEAT_CHANNEL =
            new SseChannel("system", "heartbeat");

    private final SseHub sseHub;
    private final AuctionLiveStateService stateService;
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
