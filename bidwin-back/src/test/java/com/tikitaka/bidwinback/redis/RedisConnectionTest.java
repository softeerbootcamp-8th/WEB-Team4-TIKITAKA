package com.tikitaka.bidwinback.redis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private LettuceConnectionFactory connectionFactory;

    @Test
    void redis에_값을_저장하고_읽을_수_있다() {
        redisTemplate.opsForValue().set("test:hello", "world");

        String result = redisTemplate.opsForValue().get("test:hello");

        assertThat(result).isEqualTo("world");
    }

    @Test
    void redis_명령_타임아웃은_짧게_설정돼있다() {
        // 보조 캐시인 Redis가 응답 없이 멈춰도 톰캣 스레드가 오래 묶이지 않도록,
        // 기본값(수십 초)이 아니라 짧은 타임아웃을 명시적으로 설정해야 한다.
        Duration commandTimeout = connectionFactory.getClientConfiguration().getCommandTimeout();

        assertThat(commandTimeout).isLessThanOrEqualTo(Duration.ofSeconds(1));
    }
}