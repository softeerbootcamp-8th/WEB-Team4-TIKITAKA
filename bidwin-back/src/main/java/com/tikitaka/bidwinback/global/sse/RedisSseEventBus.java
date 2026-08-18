package com.tikitaka.bidwinback.global.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.SubscriptionListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/** 커밋된 SSE 메시지를 Redis 단일 topic으로 중계해 모든 인스턴스에 전달한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSseEventBus implements MessageListener, SubscriptionListener {

    static final String TOPIC = "bidwin:sse:v1";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SseHub sseHub;

    public void publish(SseMessage<?> message) {
        try {
            redisTemplate.convertAndSend(
                    TOPIC,
                    objectMapper.writeValueAsString(message)
            );
        } catch (RuntimeException exception) {
            // Redis에 전달되지 않은 변경을 계속 기다리지 않도록 snapshot 재연결을 유도한다.
            sseHub.closeConnections();
            throw exception;
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            RedisSseMessage received = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    RedisSseMessage.class
            );
            if (sseHub.hasSubscribers(received.channel())) {
                sseHub.publish(received.toSseMessage());
            }
        } catch (RuntimeException exception) {
            // 손상된 메시지를 버리면 연결이 영구히 낡을 수 있어 snapshot부터 다시 받게 한다.
            log.atWarn()
                    .addKeyValue("event", "redis_sse_message_processing_failed")
                    .addKeyValue("failureType", exception.getClass().getSimpleName())
                    .log("Redis SSE 메시지를 처리하지 못해 로컬 연결을 종료합니다.");
            sseHub.closeConnections();
        }
    }

    @Override
    public void onChannelSubscribed(byte[] channel, long count) {
        // 장애 중 먼저 재연결된 클라이언트도 구독 복구 뒤 snapshot을 다시 받게 한다.
        sseHub.closeConnections();
    }

    void subscriptionFailed(Throwable cause) {
        log.atWarn()
                .addKeyValue("event", "redis_sse_subscription_failed")
                .addKeyValue("topic", TOPIC)
                .addKeyValue("failureType", cause.getClass().getSimpleName())
                .log("Redis SSE 구독이 끊겨 로컬 연결을 종료합니다.");
        sseHub.closeConnections();
    }

    private record RedisSseMessage(
            SseChannel channel,
            String eventName,
            long version,
            JsonNode data
    ) {

        private SseMessage<JsonNode> toSseMessage() {
            return new SseMessage<>(channel, eventName, version, data);
        }
    }
}
