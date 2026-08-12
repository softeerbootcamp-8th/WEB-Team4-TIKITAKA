package com.tikitaka.bidwinback.redis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void redis에_값을_저장하고_읽을_수_있다() {
        redisTemplate.opsForValue().set("test:hello", "world");

        String result = redisTemplate.opsForValue().get("test:hello");

        assertThat(result).isEqualTo("world");
    }
}