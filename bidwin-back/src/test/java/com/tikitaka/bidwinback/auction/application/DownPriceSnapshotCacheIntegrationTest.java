package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DownPriceSnapshotCacheIntegrationTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final LocalDateTime FIRST_GENERATION =
            LocalDateTime.of(2035, 8, 15, 10, 0, 0, 123_000_000);
    private static final LocalDateTime SECOND_GENERATION = FIRST_GENERATION.plusMinutes(1);
    private static final String KEY_PREFIX = "auction:{down-price-snapshot}:";

    private final List<String> generationTokens = new ArrayList<>();

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private SimpleMeterRegistry meterRegistry;
    private DownPriceSnapshotCache cache;

    @BeforeEach
    void setUp() {
        String host = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        RedisStandaloneConfiguration configuration =
                new RedisStandaloneConfiguration(host, port);
        String password = System.getenv().getOrDefault("REDIS_PASSWORD", "");
        if (!password.isBlank()) {
            configuration.setPassword(RedisPassword.of(password));
        }

        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        meterRegistry = new SimpleMeterRegistry();
        cache = new DownPriceSnapshotCache(redisTemplate, TTL, meterRegistry);
    }

    @AfterEach
    void tearDown() {
        for (String generation : generationTokens) {
            redisTemplate.delete(List.of(
                    generationKey(generation) + ":count",
                    generationKey(generation) + ":low",
                    generationKey(generation) + ":high"
            ));
            redisTemplate.opsForZSet().remove(KEY_PREFIX + "generations", generation);
        }
        meterRegistry.close();
        connectionFactory.destroy();
    }

    @Test
    void 세대를_원자_발행하고_asOf에_맞는_세대와_양방향_페이지를_읽는다() {
        DownPriceSnapshot first = snapshot(FIRST_GENERATION, 1L, 100L, 2L, 200L);
        DownPriceSnapshot second = snapshot(SECOND_GENERATION, 3L, 300L, 4L, 400L);
        remember(first);
        remember(second);

        cache.publish(first);
        cache.publish(second);

        DownPriceSnapshotCache.Metadata firstMetadata = cache
                .findLatestAtNotAfter(FIRST_GENERATION.plusSeconds(30))
                .orElseThrow();
        DownPriceSnapshotCache.Metadata secondMetadata = cache
                .findLatestAtNotAfter(SECOND_GENERATION)
                .orElseThrow();
        DownPriceSnapshotCache.Metadata latestMetadata = cache.findLatest().orElseThrow();

        assertThat(firstMetadata.snapshotAt()).isEqualTo(FIRST_GENERATION);
        assertThat(secondMetadata.snapshotAt()).isEqualTo(SECOND_GENERATION);
        assertThat(latestMetadata.snapshotAt()).isEqualTo(SECOND_GENERATION);
        assertThat(cache.findPage(firstMetadata, AuctionSort.PRICE_LOW, 0, 2))
                .contains(first.priceLow());
        assertThat(cache.findPage(secondMetadata, AuctionSort.PRICE_HIGH, 0, 2))
                .contains(second.priceHigh());

        String secondToken = Long.toString(toEpochMilli(SECOND_GENERATION));
        Long ttlMillis = redisTemplate.getExpire(
                generationKey(secondToken) + ":low",
                TimeUnit.MILLISECONDS
        );
        assertThat(ttlMillis).isPositive().isLessThanOrEqualTo(TTL.toMillis());
    }

    private DownPriceSnapshot snapshot(
            LocalDateTime snapshotAt,
            long lowId,
            long lowPrice,
            long highId,
            long highPrice
    ) {
        return new DownPriceSnapshot(
                snapshotAt,
                2L,
                List.of(
                        new AuctionPriceSnapshot(lowId, lowPrice),
                        new AuctionPriceSnapshot(highId, highPrice)
                ),
                List.of(
                        new AuctionPriceSnapshot(highId, highPrice),
                        new AuctionPriceSnapshot(lowId, lowPrice)
                )
        );
    }

    private void remember(DownPriceSnapshot snapshot) {
        generationTokens.add(Long.toString(toEpochMilli(snapshot.snapshotAt())));
    }

    private String generationKey(String generation) {
        return KEY_PREFIX + "generation:" + generation;
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(SEOUL).toInstant().toEpochMilli();
    }
}
