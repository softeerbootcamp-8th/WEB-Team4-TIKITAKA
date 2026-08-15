package com.tikitaka.bidwinback.auction.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.OptionalLong;

@Component
public class AuctionListCountCache {

    private static final String KEY_PREFIX = "auction:list:count:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public AuctionListCountCache(
            StringRedisTemplate redisTemplate,
            @Value("${app.auction.list-count-cache-ttl}") Duration ttl
    ) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    public OptionalLong find(AuctionListCountScope scope) {
        try {
            String value = redisTemplate.opsForValue().get(key(scope));
            if (value == null) {
                return OptionalLong.empty();
            }
            for (int index = 0; index < value.length(); index++) {
                if (value.charAt(index) < '0' || value.charAt(index) > '9') {
                    return OptionalLong.empty();
                }
            }
            long count = Long.parseLong(value);
            return count >= 0 ? OptionalLong.of(count) : OptionalLong.empty();
        } catch (RuntimeException exception) {
            return OptionalLong.empty();
        }
    }

    public void publish(AuctionListCounts counts) {
        redisTemplate.opsForValue().set(
                key(AuctionListCountScope.ALL),
                String.valueOf(counts.all()),
                ttl
        );
        redisTemplate.opsForValue().set(
                key(AuctionListCountScope.UP),
                String.valueOf(counts.up()),
                ttl
        );
        redisTemplate.opsForValue().set(
                key(AuctionListCountScope.DOWN),
                String.valueOf(counts.down()),
                ttl
        );
    }

    private String key(AuctionListCountScope scope) {
        return KEY_PREFIX + scope.name();
    }
}
