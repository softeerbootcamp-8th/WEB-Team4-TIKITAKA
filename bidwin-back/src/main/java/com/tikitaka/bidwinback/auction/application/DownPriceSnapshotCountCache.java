package com.tikitaka.bidwinback.auction.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

@Component
public class DownPriceSnapshotCountCache {

    private static final String KEY_PREFIX = "auction:down-price-snapshot:count:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;
    private final ConcurrentMap<String, CompletableFuture<Long>> inFlight = new ConcurrentHashMap<>();

    public DownPriceSnapshotCountCache(
            StringRedisTemplate redisTemplate,
            @Value("${app.auction.down-price-snapshot-retention}") Duration ttl
    ) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    public long getOrLoad(LocalDateTime snapshotAt, LongSupplier loader) {
        String key = key(snapshotAt);
        Long cachedCount = get(key);
        if (cachedCount != null) {
            return cachedCount;
        }

        CompletableFuture<Long> currentLoad = new CompletableFuture<>();
        CompletableFuture<Long> existingLoad = inFlight.putIfAbsent(key, currentLoad);
        if (existingLoad != null) {
            return await(existingLoad);
        }

        try {
            Long recheckedCount = get(key);
            if (recheckedCount != null) {
                currentLoad.complete(recheckedCount);
                return recheckedCount;
            }

            long count = loader.getAsLong();
            put(key, count);
            currentLoad.complete(count);
            return count;
        } catch (RuntimeException | Error exception) {
            currentLoad.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlight.remove(key, currentLoad);
        }
    }

    private Long get(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Long.parseLong(value) : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public void put(LocalDateTime snapshotAt, long count) {
        put(key(snapshotAt), count);
    }

    private void put(String key, long count) {
        try {
            redisTemplate.opsForValue().set(key, Long.toString(count), ttl);
        } catch (RuntimeException exception) {
            // Redis는 보조 캐시다. 저장 실패 시 다음 요청도 정확한 DB COUNT로 폴백한다.
        }
    }

    private long await(CompletableFuture<Long> load) {
        try {
            return load.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (exception.getCause() instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    private static String key(LocalDateTime snapshotAt) {
        return KEY_PREFIX + snapshotAt;
    }
}
