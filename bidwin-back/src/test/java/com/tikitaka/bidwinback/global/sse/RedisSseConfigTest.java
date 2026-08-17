package com.tikitaka.bidwinback.global.sse;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisSseConfigTest {

    @Test
    void Redis_topic_구독_연결이_끊기면_로컬_SSE_연결을_종료한다() throws Exception {
        // given
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        SseHub sseHub = mock(SseHub.class);
        RedisSseEventBus eventBus = new RedisSseEventBus(
                mock(StringRedisTemplate.class),
                new ObjectMapper(),
                sseHub
        );
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.isSubscribed()).thenReturn(false);
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(connection).subscribe(
                        any(MessageListener.class),
                        any(byte[][].class)
                );
        RedisMessageListenerContainer container = new RedisSseConfig()
                .redisSseListenerContainer(connectionFactory, eventBus);
        container.setRecoveryBackoff(new FixedBackOff(0L, 0L));
        container.afterPropertiesSet();

        try {
            // when & then
            assertThatThrownBy(container::start).isInstanceOf(RuntimeException.class);
            verify(sseHub).closeConnections();
        } finally {
            container.destroy();
        }
    }
}
