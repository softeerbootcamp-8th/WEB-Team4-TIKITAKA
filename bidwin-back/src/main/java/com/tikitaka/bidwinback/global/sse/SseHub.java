package com.tikitaka.bidwinback.global.sse;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_INPUT_VALUE;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.SSE_CONNECTION_LIMIT_EXCEEDED;

/** 도메인과 무관하게 채널별 SSE 연결을 색인하고 메시지를 전파한다. */
@Component
public class SseHub {

    private final ConcurrentHashMap<SseChannel, Set<SseConnection>> connectionsByChannel =
            new ConcurrentHashMap<>();
    private final Set<SseConnection> connections = ConcurrentHashMap.newKeySet();
    private final long timeoutMs;
    private final long reconnectTimeMs;
    private final int maxChannelsPerConnection;
    private final int maxPendingMessagesPerConnection;
    private final int maxConnections;
    private final Semaphore connectionPermits;

    public SseHub(
            @Value("${app.sse.connection-timeout-ms:300000}") long timeoutMs,
            @Value("${app.sse.reconnect-time-ms:3000}") long reconnectTimeMs,
            @Value("${app.sse.max-channels-per-connection:50}")
            int maxChannelsPerConnection,
            @Value("${app.sse.max-pending-messages-per-connection:100}")
            int maxPendingMessagesPerConnection,
            @Value("${app.sse.max-connections:1000}")
            int maxConnections
    ) {
        if (maxPendingMessagesPerConnection <= 0) {
            throw new IllegalArgumentException("SSE 대기열 상한은 양수여야 합니다.");
        }
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("SSE 연결 상한은 양수여야 합니다.");
        }
        this.timeoutMs = timeoutMs;
        this.reconnectTimeMs = reconnectTimeMs;
        this.maxChannelsPerConnection = maxChannelsPerConnection;
        this.maxPendingMessagesPerConnection = maxPendingMessagesPerConnection;
        this.maxConnections = maxConnections;
        this.connectionPermits = new Semaphore(maxConnections);
    }

    public SseEmitter subscribe(
            Collection<SseChannel> channels,
            Supplier<? extends Collection<? extends SseMessage<?>>> initialMessages
    ) {
        return subscribe(channels, new SseEmitter(timeoutMs), initialMessages);
    }

    SseEmitter subscribe(
            Collection<SseChannel> channels,
            SseEmitter emitter,
            Supplier<? extends Collection<? extends SseMessage<?>>> initialMessages
    ) {
        Set<SseChannel> subscriptions = new LinkedHashSet<>(channels);
        validateSubscriptions(subscriptions);
        SseConnection connection = new SseConnection(
                emitter,
                reconnectTimeMs,
                maxPendingMessagesPerConnection,
                closed -> unsubscribe(subscriptions, closed)
        );
        // 검사와 등록 사이의 경쟁으로 상한을 넘지 않도록 연결 자리를 먼저 원자적으로 예약한다.
        reserveConnection();

        try {
            // snapshot 조회 중 발행된 변경도 받도록 색인과 writer를 먼저 활성화한다.
            connections.add(connection);
            // 일부 채널만 색인된 순간 연결이 종료돼도, 해지가 전체 등록 뒤 정리하게 한다.
            synchronized (connection) {
                subscriptions.forEach(channel ->
                        connectionsByChannel.compute(channel, (ignored, subscribers) -> {
                            Set<SseConnection> indexed = subscribers == null
                                    ? ConcurrentHashMap.newKeySet()
                                    : subscribers;
                            // 마지막 연결의 해지와 겹쳐도 맵에 연결이 없는 순간이 생기지 않게 한다.
                            indexed.add(connection);
                            return indexed;
                        })
                );
            }
            connection.activate();
            initialMessages.get().forEach(message -> {
                if (!subscriptions.contains(message.channel())) {
                    throw new IllegalArgumentException("구독하지 않은 채널의 초기 메시지입니다.");
                }
                connection.send(message);
            });
            return emitter;
        } catch (RuntimeException exception) {
            connection.close();
            throw exception;
        }
    }

    private void unsubscribe(Set<SseChannel> channels, SseConnection connection) {
        boolean registered = connections.remove(connection);
        // terminate가 이 콜백을 한 번만 호출하므로, 등록 도중 실패한 예약도 정확히 한 번 반환한다.
        connectionPermits.release();
        if (!registered) {
            return;
        }
        synchronized (connection) {
            channels.forEach(channel ->
                    connectionsByChannel.computeIfPresent(channel, (ignored, subscribers) -> {
                        subscribers.remove(connection);
                        return subscribers.isEmpty() ? null : subscribers;
                    })
            );
        }
    }

    public void publish(SseMessage<?> message) {
        Set<SseConnection> subscribers = connectionsByChannel.get(message.channel());
        if (subscribers != null) {
            subscribers.forEach(connection -> connection.send(message));
        }
    }

    public void broadcast(SseMessage<?> message) {
        connections.forEach(connection -> connection.send(message));
    }

    public boolean hasSubscribers(SseChannel channel) {
        return connectionsByChannel.containsKey(channel);
    }

    public boolean hasConnections() {
        return !connections.isEmpty();
    }

    int connectionCount() {
        return connections.size();
    }

    @PreDestroy
    void closeConnections() {
        connections.forEach(SseConnection::close);
    }

    private void validateSubscriptions(Set<SseChannel> channels) {
        if (channels.isEmpty()
                || channels.contains(null)
                || channels.size() > maxChannelsPerConnection) {
            throw new SseException(
                    INVALID_INPUT_VALUE,
                    "한 연결에서 구독할 채널은 1개 이상 "
                            + maxChannelsPerConnection + "개 이하여야 합니다."
            );
        }
    }

    // 초과분은 gateway가 아닌 애플리케이션에서도 막아, 반복 재연결이 서버 자원을 고갈시키지 못하게 한다.
    private void reserveConnection() {
        if (!connectionPermits.tryAcquire()) {
            throw new SseException(
                    SSE_CONNECTION_LIMIT_EXCEEDED,
                    "전체 SSE 연결 상한(" + maxConnections + ")을 초과했습니다."
            );
        }
    }

    private long jitteredTimeoutMs() {
        // 만료된 연결의 재구독이 한순간에 몰리지 않도록 기본 timeout을 ±10% 분산한다.
        long jitter = timeoutMs / 10;
        return timeoutMs + ThreadLocalRandom.current().nextLong(-jitter, jitter + 1);
    }

}
