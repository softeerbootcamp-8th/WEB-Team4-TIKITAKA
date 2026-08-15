package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Set;

@Component
public class DownPriceSnapshotCache {

    private static final String KEY_PREFIX = "auction:{down-price-snapshot}:";
    private static final String GENERATION_INDEX_KEY = KEY_PREFIX + "generations";
    private static final String CAPTURE_LOCK_KEY = KEY_PREFIX + "capture-lock";
    private static final Duration CAPTURE_LOCK_TTL = Duration.ofSeconds(50);
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private static final RedisScript<Long> PUBLISH_SCRIPT = new DefaultRedisScript<>(
            """
                local ttl = ARGV[2]
                local position = 4
                local lowCount = tonumber(ARGV[3])

                redis.call('DEL', KEYS[2], KEYS[3])
                if lowCount > 0 then
                    redis.call('RPUSH', KEYS[2], unpack(ARGV, position, position + lowCount - 1))
                    redis.call('PEXPIRE', KEYS[2], ttl)
                end
                position = position + lowCount

                local highCount = tonumber(ARGV[position])
                position = position + 1
                if highCount > 0 then
                    redis.call('RPUSH', KEYS[3], unpack(ARGV, position, position + highCount - 1))
                    redis.call('PEXPIRE', KEYS[3], ttl)
                end
                position = position + highCount

                local generation = ARGV[position]
                local expiredBefore = ARGV[position + 1]
                redis.call('SET', KEYS[1], ARGV[1], 'PX', ttl)
                redis.call('ZADD', KEYS[4], generation, generation)
                redis.call('ZREMRANGEBYSCORE', KEYS[4], '-inf', expiredBefore)
                redis.call('PEXPIRE', KEYS[4], ttl)
                return 1
                """,
            Long.class
    );

    private static final RedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            """
                if redis.call('GET', KEYS[1]) == ARGV[1] then
                    return redis.call('DEL', KEYS[1])
                end
                return 0
                """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public DownPriceSnapshotCache(
            StringRedisTemplate redisTemplate,
            @Value("${app.auction.down-price-snapshot-ttl}") Duration ttl
    ) {
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("가격 스냅샷 TTL은 0보다 커야 합니다.");
        }
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    public boolean tryAcquireCaptureLock(String owner) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                    CAPTURE_LOCK_KEY,
                    owner,
                    CAPTURE_LOCK_TTL
            ));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public void releaseCaptureLock(String owner) {
        try {
            redisTemplate.execute(
                    RELEASE_LOCK_SCRIPT,
                    List.of(CAPTURE_LOCK_KEY),
                    owner
            );
        } catch (RuntimeException exception) {
            // 짧은 TTL의 보조 락이다. 해제 실패 시 다음 만료 후 다시 적재한다.
        }
    }

    public void publish(DownPriceSnapshot snapshot) {
        long generation = toEpochMilli(snapshot.snapshotAt());
        String generationToken = Long.toString(generation);
        List<String> arguments = new ArrayList<>(
                snapshot.priceLow().size() + snapshot.priceHigh().size() + 7
        );
        arguments.add(Long.toString(snapshot.totalCount()));
        arguments.add(Long.toString(ttl.toMillis()));
        arguments.add(Integer.toString(snapshot.priceLow().size()));
        snapshot.priceLow().stream().map(this::serialize).forEach(arguments::add);
        arguments.add(Integer.toString(snapshot.priceHigh().size()));
        snapshot.priceHigh().stream().map(this::serialize).forEach(arguments::add);
        arguments.add(generationToken);
        arguments.add(Long.toString(generation - ttl.toMillis()));

        Long result = redisTemplate.execute(
                PUBLISH_SCRIPT,
                List.of(
                        countKey(generationToken),
                        listKey(generationToken, AuctionSort.PRICE_LOW),
                        listKey(generationToken, AuctionSort.PRICE_HIGH),
                        GENERATION_INDEX_KEY
                ),
                arguments.toArray()
        );
        if (result == null || result != 1L) {
            throw new IllegalStateException("하향 가격 스냅샷을 Redis에 발행하지 못했습니다.");
        }
    }

    public Optional<Metadata> findLatestAtNotAfter(LocalDateTime asOf) {
        try {
            Set<String> generations = redisTemplate.opsForZSet().reverseRangeByScore(
                    GENERATION_INDEX_KEY,
                    0,
                    toEpochMilli(asOf),
                    0,
                    1
            );
            if (generations == null || generations.isEmpty()) {
                return Optional.empty();
            }

            String generationToken = generations.iterator().next();
            String count = redisTemplate.opsForValue().get(countKey(generationToken));
            if (count == null) {
                return Optional.empty();
            }
            long totalCount = Long.parseLong(count);
            if (totalCount < 0) {
                return Optional.empty();
            }
            return Optional.of(new Metadata(
                    fromEpochMilli(Long.parseLong(generationToken)),
                    totalCount
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public Optional<List<AuctionPriceSnapshot>> findPage(
            Metadata metadata,
            AuctionSort sort,
            long offset,
            int limit
    ) {
        try {
            long toExclusive = Math.min(
                    Math.addExact(offset, limit),
                    metadata.totalCount()
            );
            long cachedSize = Math.min(
                    metadata.totalCount(),
                    DownPriceSnapshot.MAX_ENTRIES
            );
            if (offset < 0 || limit <= 0 || offset >= metadata.totalCount()) {
                return Optional.of(List.of());
            }
            if (toExclusive > cachedSize) {
                return Optional.empty();
            }

            String generationToken = Long.toString(toEpochMilli(metadata.snapshotAt()));
            List<String> values = redisTemplate.opsForList().range(
                    listKey(generationToken, sort),
                    offset,
                    toExclusive - 1
            );
            int expectedSize = Math.toIntExact(toExclusive - offset);
            if (values == null || values.size() != expectedSize) {
                return Optional.empty();
            }
            List<AuctionPriceSnapshot> snapshots = values.stream()
                    .map(this::deserialize)
                    .toList();
            return Optional.of(snapshots);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String serialize(AuctionPriceSnapshot snapshot) {
        return snapshot.auctionId() + ":" + snapshot.currentPrice();
    }

    private AuctionPriceSnapshot deserialize(String value) {
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("잘못된 가격 스냅샷 값입니다.");
        }
        return new AuctionPriceSnapshot(
                Long.parseLong(value.substring(0, separator)),
                Long.parseLong(value.substring(separator + 1))
        );
    }

    private String countKey(String generation) {
        return generationKey(generation) + ":count";
    }

    private String listKey(String generation, AuctionSort sort) {
        return generationKey(generation) + switch (sort) {
            case PRICE_LOW -> ":low";
            case PRICE_HIGH -> ":high";
            case RECOMMENDED, DEADLINE, LATEST ->
                    throw new IllegalArgumentException("가격 정렬이 아닙니다: " + sort);
        };
    }

    private String generationKey(String generation) {
        return KEY_PREFIX + "generation:" + generation;
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(SERVICE_ZONE).toInstant().toEpochMilli();
    }

    private LocalDateTime fromEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), SERVICE_ZONE);
    }

    public record Metadata(LocalDateTime snapshotAt, long totalCount) {
    }
}
