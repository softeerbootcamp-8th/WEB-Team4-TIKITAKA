package com.tikitaka.bidwinback.global.sse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.util.backoff.BackOffExecution;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Configuration(proxyBeanMethods = false)
public class RedisSseConfig {

    @Bean
    RedisMessageListenerContainer redisSseListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisSseEventBus eventBus
    ) {
        RedisMessageListenerContainer container =
                new SseAwareRedisMessageListenerContainer(eventBus::subscriptionFailed);
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(eventBus, new ChannelTopic(RedisSseEventBus.TOPIC));
        return container;
    }
}

/** Redis가 복구를 재시도하기 전에 현재 인스턴스의 SSE 연결부터 snapshot 경로로 돌린다. */
class SseAwareRedisMessageListenerContainer extends RedisMessageListenerContainer {

    private final Consumer<Throwable> subscriptionFailureHandler;

    SseAwareRedisMessageListenerContainer(Consumer<Throwable> subscriptionFailureHandler) {
        this.subscriptionFailureHandler = subscriptionFailureHandler;
    }

    @Override
    protected void handleSubscriptionException(
            CompletableFuture<Void> future,
            BackOffExecution backOffExecution,
            Throwable cause
    ) {
        subscriptionFailureHandler.accept(cause);
        super.handleSubscriptionException(future, backOffExecution, cause);
    }
}
