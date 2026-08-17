package com.tikitaka.bidwinback.global.sse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisSseEventBusTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SseHub sseHub;

    private ObjectMapper objectMapper;
    private RedisSseEventBus eventBus;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        eventBus = new RedisSseEventBus(redisTemplate, objectMapper, sseHub);
    }

    @Test
    void Redis로_발행한_SSE_메시지는_수신_인스턴스의_로컬_구독자에게_그대로_전달한다() {
        // given
        SseMessage<Map<String, Long>> original = new SseMessage<>(
                new SseChannel("auction", "1"),
                "auction-state",
                3L,
                Map.of("auctionId", 1L, "revision", 3L)
        );
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        when(redisTemplate.convertAndSend(
                org.mockito.ArgumentMatchers.eq(RedisSseEventBus.TOPIC),
                payload.capture()
        )).thenReturn(1L);
        when(sseHub.hasSubscribers(original.channel())).thenReturn(true);

        // when
        eventBus.publish(original);
        eventBus.onMessage(message(payload.getValue()), null);

        // then
        ArgumentCaptor<SseMessage<?>> delivered = sseMessageCaptor();
        verify(sseHub).publish(delivered.capture());
        assertThat(delivered.getValue().channel()).isEqualTo(original.channel());
        assertThat(delivered.getValue().eventName()).isEqualTo(original.eventName());
        assertThat(delivered.getValue().version()).isEqualTo(original.version());
        assertThat(delivered.getValue().data().toString())
                .isEqualTo(objectMapper.valueToTree(original.data()).toString());
    }

    @Test
    void Redis_메시지의_채널에_로컬_구독자가_없으면_SSE로_전달하지_않는다() {
        // given
        SseMessage<Map<String, Long>> message = new SseMessage<>(
                new SseChannel("auction", "1"),
                "auction-state",
                3L,
                Map.of("auctionId", 1L)
        );
        when(sseHub.hasSubscribers(message.channel())).thenReturn(false);

        // when
        eventBus.onMessage(message(objectMapper.writeValueAsString(message)), null);

        // then
        verify(sseHub, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 손상된_Redis_메시지를_받으면_로컬_SSE_연결을_종료한다() {
        // given
        DefaultMessage malformed = message("not-json");

        // when
        eventBus.onMessage(malformed, null);

        // then
        verify(sseHub).closeConnections();
    }

    @Test
    void Redis_발행이_실패하면_로컬_SSE_연결을_종료하고_실패를_알린다() {
        // given
        SseMessage<Long> message = new SseMessage<>(
                new SseChannel("auction", "1"),
                "auction-state",
                3L,
                1L
        );
        when(redisTemplate.convertAndSend(anyString(), anyString()))
                .thenThrow(new IllegalStateException("redis down"));

        // when & then
        assertThatThrownBy(() -> eventBus.publish(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis down");
        verify(sseHub).closeConnections();
    }

    @Test
    void Redis_구독이_복구되면_장애중_연결된_클라이언트도_snapshot을_다시_받게_한다() {
        // given
        byte[] topic = RedisSseEventBus.TOPIC.getBytes(StandardCharsets.UTF_8);

        // when
        eventBus.onChannelSubscribed(topic, 1L);

        // then
        verify(sseHub).closeConnections();
    }

    @Test
    void Redis_구독이_실패하면_로컬_SSE_연결을_종료한다() {
        // given
        IllegalStateException failure = new IllegalStateException("redis down");

        // when
        eventBus.subscriptionFailed(failure);

        // then
        verify(sseHub).closeConnections();
    }

    private DefaultMessage message(String payload) {
        return new DefaultMessage(
                RedisSseEventBus.TOPIC.getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8)
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<SseMessage<?>> sseMessageCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(SseMessage.class);
    }
}
