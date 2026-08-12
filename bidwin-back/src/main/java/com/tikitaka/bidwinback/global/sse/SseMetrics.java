package com.tikitaka.bidwinback.global.sse;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** SSE의 서버 구간 처리량, 지연, 실패와 현재 자원 점유를 노출한다. */
@Component
final class SseMetrics {

    private final MeterRegistry registry;

    SseMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void bind(
            Set<SseConnection> connections,
            int maxConnections,
            int maxPendingMessagesPerConnection
    ) {
        Gauge.builder("bidwin.sse.connections.active", connections, Set::size)
                .description("현재 활성 SSE 연결 수")
                .register(registry);
        Gauge.builder("bidwin.sse.connections.limit", () -> maxConnections)
                .description("설정된 SSE 연결 상한")
                .register(registry);
        Gauge.builder(
                        "bidwin.sse.messages.pending",
                        connections,
                        activeConnections -> activeConnections.stream()
                                .mapToInt(SseConnection::pendingMessageCount)
                                .sum()
                )
                .description("모든 SSE 연결에서 전송을 기다리는 메시지 수")
                .register(registry);
        Gauge.builder(
                        "bidwin.sse.messages.pending.max",
                        connections,
                        activeConnections -> activeConnections.stream()
                                .mapToInt(SseConnection::pendingMessageCount)
                                .max()
                                .orElse(0)
                )
                .description("단일 SSE 연결의 최대 대기 메시지 수")
                .register(registry);
        Gauge.builder(
                        "bidwin.sse.messages.pending.limit",
                        () -> maxPendingMessagesPerConnection
                )
                .description("연결당 SSE 대기열 상한")
                .register(registry);
    }

    void recordPublished(SseMessage<?> message) {
        eventCounter(message, "published", "none").increment();
    }

    void recordSent(
            SseMessage<?> message,
            long enqueuedAtNanos,
            long writeStartedAtNanos
    ) {
        long completedAtNanos = System.nanoTime();
        eventCounter(message, "sent", "none").increment();
        timer("bidwin.sse.delivery", message)
                .record(completedAtNanos - enqueuedAtNanos, TimeUnit.NANOSECONDS);
        timer("bidwin.sse.queue", message)
                .record(writeStartedAtNanos - enqueuedAtNanos, TimeUnit.NANOSECONDS);
        timer("bidwin.sse.write", message)
                .record(completedAtNanos - writeStartedAtNanos, TimeUnit.NANOSECONDS);
    }

    void recordPrepared(SseMessage<?> message, long preparationStartedAtNanos) {
        timer("bidwin.sse.preparation", message)
                .record(
                        System.nanoTime() - preparationStartedAtNanos,
                        TimeUnit.NANOSECONDS
                );
    }

    void recordSuppressed(SseMessage<?> message) {
        eventCounter(message, "suppressed", "stale_version").increment();
    }

    void recordFailed(SseMessage<?> message, String reason) {
        eventCounter(message, "failed", reason).increment();
    }

    void recordClosed(String reason) {
        registry.counter("bidwin.sse.connections.closed", "reason", reason).increment();
    }

    void recordRejected() {
        registry.counter("bidwin.sse.connections.rejected").increment();
    }

    private io.micrometer.core.instrument.Counter eventCounter(
            SseMessage<?> message,
            String result,
            String reason
    ) {
        return registry.counter(
                "bidwin.sse.events",
                Tags.of(
                        "namespace", message.channel().namespace(),
                        "event", message.eventName(),
                        "result", result,
                        "reason", reason
                )
        );
    }

    private Timer timer(String name, SseMessage<?> message) {
        return Timer.builder(name)
                .tags(
                        "namespace", message.channel().namespace(),
                        "event", message.eventName()
                )
                .publishPercentileHistogram()
                .serviceLevelObjectives(
                        Duration.ofMillis(10),
                        Duration.ofMillis(50),
                        Duration.ofMillis(100),
                        Duration.ofMillis(500),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(5)
                )
                .register(registry);
    }
}
