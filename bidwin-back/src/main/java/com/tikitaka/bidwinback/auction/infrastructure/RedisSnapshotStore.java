package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.DownPriceSnapshot;
import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotMetrics;
import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotBuildKey;
import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotPage;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;

@Slf4j
@Component
public class RedisSnapshotStore {

    private static final String KEY_PREFIX = "auction:{down-price}:";
    private static final String GENERATION_PREFIX = KEY_PREFIX + "generation:";
    private static final String GENERATIONS_KEY = KEY_PREFIX + "generations";
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private static final RedisScript<Long> PUBLISH_SCRIPT = new DefaultRedisScript<>(
            """
                local generation = ARGV[1]
                local ttl = ARGV[2]
                local expiredBefore = ARGV[3]
                local lowCount = tonumber(ARGV[4])
                local highCount = tonumber(ARGV[5])
                local generationPrefix = ARGV[6]
                local position = 7

                redis.call('DEL', KEYS[2], KEYS[3], KEYS[4])
                redis.call(
                    'HSET', KEYS[2],
                    'generationAt', generation,
                    'lowSize', lowCount,
                    'highSize', highCount
                )
                redis.call('PEXPIRE', KEYS[2], ttl)

                if lowCount > 0 then
                    redis.call('RPUSH', KEYS[3], unpack(ARGV, position, position + lowCount - 1))
                    redis.call('PEXPIRE', KEYS[3], ttl)
                end
                position = position + lowCount

                if highCount > 0 then
                    redis.call('RPUSH', KEYS[4], unpack(ARGV, position, position + highCount - 1))
                    redis.call('PEXPIRE', KEYS[4], ttl)
                end

                redis.call('ZADD', KEYS[1], generation, generation)
                local expired = redis.call(
                    'ZRANGEBYSCORE', KEYS[1], '-inf', expiredBefore
                )
                for _, oldGeneration in ipairs(expired) do
                    local oldPrefix = generationPrefix .. oldGeneration
                    redis.call(
                        'DEL',
                        oldPrefix .. ':meta',
                        oldPrefix .. ':low',
                        oldPrefix .. ':high'
                    )
                end
                redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', expiredBefore)
                redis.call('PEXPIRE', KEYS[1], ttl)
                return 1
                """,
            Long.class
    );

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> FIND_LATEST_PAGE_SCRIPT = new DefaultRedisScript<>(
            """
                local generations = redis.call('ZREVRANGE', KEYS[1], 0, -1)
                for _, generation in ipairs(generations) do
                    local generationPrefix = ARGV[1] .. generation
                    local metaKey = generationPrefix .. ':meta'
                    if redis.call('EXISTS', metaKey) == 1 then
                        local count = redis.call('HGET', metaKey, ARGV[2])
                        if not count then
                            return {'CORRUPT'}
                        end
                        local values = redis.call(
                            'LRANGE', generationPrefix .. ARGV[3], ARGV[4], ARGV[5]
                        )
                        local result = {generation, count}
                        for _, value in ipairs(values) do
                            table.insert(result, value)
                        end
                        return result
                    end
                    redis.call('ZREM', KEYS[1], generation)
                end
                return {}
                """,
            List.class
    );

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> FIND_EXACT_PAGE_SCRIPT = new DefaultRedisScript<>(
            """
                if redis.call('EXISTS', KEYS[1]) == 0 then
                    return {}
                end
                local count = redis.call('HGET', KEYS[1], ARGV[2])
                if not count then
                    return {'CORRUPT'}
                end
                local values = redis.call('LRANGE', KEYS[2], ARGV[3], ARGV[4])
                local result = {ARGV[1], count}
                for _, value in ipairs(values) do
                    table.insert(result, value)
                end
                return result
                """,
            List.class
    );

    private final StringRedisTemplate redisTemplate;
    private final RedisSnapshotCircuitBreaker circuitBreaker;
    private final DownPriceSnapshotMetrics metrics;
    private final Duration retention;
    private final Duration captureLockTtl;

    public RedisSnapshotStore(
            StringRedisTemplate redisTemplate,
            RedisSnapshotCircuitBreaker circuitBreaker,
            DownPriceSnapshotMetrics metrics,
            @Value("${app.auction.down-price-snapshot.retention}") Duration retention,
            @Value("${app.auction.down-price-snapshot.refresh-interval}")
            Duration captureLockTtl
    ) {
        this.redisTemplate = redisTemplate;
        this.circuitBreaker = circuitBreaker;
        this.metrics = metrics;
        this.retention = retention;
        this.captureLockTtl = captureLockTtl;
    }

    public void publish(DownPriceSnapshot snapshot) {
        try {
            execute(() -> publishToRedis(snapshot));
            metrics.recordPublish(true);
        } catch (RuntimeException exception) {
            metrics.recordPublish(false);
            throw exception;
        }
    }

    public Optional<DownPriceSnapshotPage> findLatestPage(
            AuctionSort sort,
            int page,
            int size
    ) {
        return execute(() -> parsePage(
                redisTemplate.execute(
                        FIND_LATEST_PAGE_SCRIPT,
                        List.of(GENERATIONS_KEY),
                        GENERATION_PREFIX,
                        countField(sort),
                        listSuffix(sort),
                        Long.toString(offset(page, size)),
                        Long.toString(lastIndex(page, size))
                ),
                page,
                size
        ));
    }

    public Optional<DownPriceSnapshotPage> findExactPage(
            LocalDateTime generationAt,
            AuctionSort sort,
            int page,
            int size
    ) {
        String generation = Long.toString(toEpochMilli(generationAt));
        String generationPrefix = GENERATION_PREFIX + generation;
        return execute(() -> parsePage(
                redisTemplate.execute(
                        FIND_EXACT_PAGE_SCRIPT,
                        List.of(
                                generationPrefix + ":meta",
                                generationPrefix + listSuffix(sort)
                        ),
                        generation,
                        countField(sort),
                        Long.toString(offset(page, size)),
                        Long.toString(lastIndex(page, size))
                ),
                page,
                size
        ));
    }

    public boolean tryAcquireCaptureLock(DownPriceSnapshotBuildKey key) {
        String slot = Long.toString(toEpochMilli(key.generationAt()));
        return execute(() -> Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(
                        KEY_PREFIX + "capture-lock:" + slot,
                        slot,
                        captureLockTtl
                )
        ));
    }

    public void refreshEvictionMetric() {
        try {
            Properties stats = redisTemplate.execute(
                    (RedisCallback<Properties>) connection ->
                            connection.serverCommands().info("stats")
            );
            if (stats != null) {
                metrics.recordRedisEvictions(Long.parseLong(
                        stats.getProperty("evicted_keys", "0")
                ));
            }
        } catch (RuntimeException exception) {
            // 관측용 INFO 실패가 스냅샷 조회 circuit나 응답 경로에 영향을 주면 안 된다.
            log.debug("Redis eviction 메트릭을 조회하지 못했습니다.", exception);
        }
    }

    private Long publishToRedis(DownPriceSnapshot snapshot) {
        String generation = Long.toString(toEpochMilli(snapshot.generationAt()));
        List<String> arguments = new ArrayList<>(
                snapshot.priceLow().size() + snapshot.priceHigh().size() + 6
        );
        arguments.add(generation);
        arguments.add(Long.toString(retention.toMillis()));
        arguments.add(Long.toString(Long.parseLong(generation) - retention.toMillis()));
        arguments.add(Integer.toString(snapshot.priceLow().size()));
        arguments.add(Integer.toString(snapshot.priceHigh().size()));
        arguments.add(GENERATION_PREFIX);
        snapshot.priceLow().stream().map(this::serialize).forEach(arguments::add);
        snapshot.priceHigh().stream().map(this::serialize).forEach(arguments::add);

        String generationPrefix = GENERATION_PREFIX + generation;
        Long result = redisTemplate.execute(
                PUBLISH_SCRIPT,
                List.of(
                        GENERATIONS_KEY,
                        generationPrefix + ":meta",
                        generationPrefix + ":low",
                        generationPrefix + ":high"
                ),
                arguments.toArray()
        );
        if (result == null || result != 1L) {
            throw new IllegalStateException("하향 가격 스냅샷을 Redis에 발행하지 못했습니다.");
        }
        return result;
    }

    private Optional<DownPriceSnapshotPage> parsePage(
            List<?> values,
            int page,
            int size
    ) {
        if (values == null) {
            throw new IllegalStateException("Redis 스냅샷 조회 결과가 없습니다.");
        }
        if (values.isEmpty()) {
            return Optional.empty();
        }
        if (values.size() < 2 || "CORRUPT".equals(values.getFirst().toString())) {
            throw new IllegalStateException("Redis 스냅샷 세대가 손상됐습니다.");
        }

        LocalDateTime generationAt = fromEpochMilli(parseLong(values.get(0)));
        int totalCount = Math.toIntExact(parseLong(values.get(1)));
        if (totalCount < 0 || totalCount > DownPriceSnapshot.MAX_ENTRIES_PER_SORT) {
            throw new IllegalStateException("Redis 스냅샷 전체 건수가 유효하지 않습니다.");
        }
        int expectedSize = Math.min(size, Math.max(0, totalCount - (int) offset(page, size)));
        if (values.size() - 2 != expectedSize) {
            throw new IllegalStateException("Redis 스냅샷 페이지 길이가 일치하지 않습니다.");
        }
        List<AuctionPriceSnapshot> entries = values.subList(2, values.size())
                .stream()
                .map(value -> deserialize(value.toString()))
                .toList();
        return Optional.of(new DownPriceSnapshotPage(generationAt, entries, totalCount));
    }

    private <T> T execute(Supplier<T> operation) {
        if (!circuitBreaker.tryAcquirePermission()) {
            throw new RedisSnapshotUnavailableException("Redis snapshot circuit가 열려 있습니다.");
        }
        try {
            T result = operation.get();
            circuitBreaker.recordSuccess();
            return result;
        } catch (RuntimeException exception) {
            circuitBreaker.recordFailure();
            throw new RedisSnapshotUnavailableException(
                    "Redis snapshot 저장소를 사용할 수 없습니다.",
                    exception
            );
        } catch (Error error) {
            circuitBreaker.recordFailure();
            throw error;
        }
    }

    private String serialize(AuctionPriceSnapshot snapshot) {
        return snapshot.auctionId()
                + ":" + snapshot.sortPrice()
                + ":" + snapshot.displayPrice();
    }

    private AuctionPriceSnapshot deserialize(String value) {
        String[] fields = value.split(":", -1);
        if (fields.length != 3) {
            throw new IllegalArgumentException("잘못된 가격 스냅샷 값입니다.");
        }
        return new AuctionPriceSnapshot(
                Long.parseLong(fields[0]),
                Long.parseLong(fields[1]),
                Long.parseLong(fields[2])
        );
    }

    private String countField(AuctionSort sort) {
        return switch (sort) {
            case PRICE_LOW -> "lowSize";
            case PRICE_HIGH -> "highSize";
            case RECOMMENDED, DEADLINE, LATEST -> invalidSort(sort);
        };
    }

    private String listSuffix(AuctionSort sort) {
        return switch (sort) {
            case PRICE_LOW -> ":low";
            case PRICE_HIGH -> ":high";
            case RECOMMENDED, DEADLINE, LATEST -> invalidSort(sort);
        };
    }

    private String invalidSort(AuctionSort sort) {
        throw new IllegalArgumentException("가격 정렬이 아닙니다: " + sort);
    }

    private long offset(int page, int size) {
        return Math.multiplyExact((long) page - 1L, size);
    }

    private long lastIndex(int page, int size) {
        return Math.addExact(offset(page, size), size - 1L);
    }

    private long parseLong(Object value) {
        return Long.parseLong(value.toString());
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(SERVICE_ZONE).toInstant().toEpochMilli();
    }

    private LocalDateTime fromEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), SERVICE_ZONE);
    }
}
