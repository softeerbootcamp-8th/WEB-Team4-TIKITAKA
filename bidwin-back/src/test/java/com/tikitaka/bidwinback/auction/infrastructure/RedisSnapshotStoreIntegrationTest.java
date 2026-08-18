package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.DownPriceSnapshot;
import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotMetrics;
import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotPage;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisSnapshotStoreIntegrationTest {

    private static final int REDIS_PORT = 6379;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4")
    ).withExposedPorts(REDIS_PORT);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisSnapshotStore store;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(),
                REDIS.getMappedPort(REDIS_PORT)
        );
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
        DownPriceSnapshotMetrics metrics = new DownPriceSnapshotMetrics(
                new SimpleMeterRegistry()
        );
        store = new RedisSnapshotStore(
                redisTemplate,
                new RedisSnapshotCircuitBreaker(
                        Duration.ofSeconds(5),
                        System::nanoTime,
                        metrics
                ),
                metrics,
                Duration.ofMinutes(10),
                Duration.ofSeconds(30)
        );
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void 정렬별_1600건을_발행하고_latest와_exact_페이지를_조회한다() {
        LocalDateTime generationAt = LocalDateTime.of(2026, 8, 18, 12, 0, 30);
        List<AuctionPriceSnapshot> priceLow = snapshots(0L, 1_600);
        List<AuctionPriceSnapshot> priceHigh = snapshots(10_000L, 1_600);

        store.publish(new DownPriceSnapshot(generationAt, priceLow, priceHigh));

        DownPriceSnapshotPage exact = store.findExactPage(
                generationAt,
                AuctionSort.PRICE_LOW,
                100,
                16
        ).orElseThrow();
        DownPriceSnapshotPage latest = store.findLatestPage(
                AuctionSort.PRICE_HIGH,
                100,
                16
        ).orElseThrow();

        assertThat(exact.totalCount()).isEqualTo(1_600);
        assertThat(exact.entries()).containsExactlyElementsOf(priceLow.subList(1_584, 1_600));
        assertThat(latest.generationAt()).isEqualTo(generationAt);
        assertThat(latest.totalCount()).isEqualTo(1_600);
        assertThat(latest.entries()).containsExactlyElementsOf(priceHigh.subList(1_584, 1_600));
        assertThat(redisTemplate.getExpire(
                generationPrefix(generationAt) + ":meta",
                TimeUnit.MILLISECONDS
        )).isBetween(1L, Duration.ofMinutes(10).toMillis());
    }

    @Test
    void 새_세대를_발행하면_10분이_지난_세대를_목록과_데이터에서_제거한다() {
        LocalDateTime expired = LocalDateTime.of(2026, 8, 18, 12, 0);
        LocalDateTime latest = expired.plusMinutes(10).plusSeconds(30);
        store.publish(snapshot(expired, 1L));

        store.publish(snapshot(latest, 2L));

        assertThat(store.findExactPage(
                expired,
                AuctionSort.PRICE_LOW,
                1,
                16
        )).isEmpty();
        assertThat(store.findLatestPage(
                AuctionSort.PRICE_LOW,
                1,
                16
        )).get().extracting(DownPriceSnapshotPage::generationAt).isEqualTo(latest);
        assertThat(redisTemplate.hasKey(generationPrefix(expired) + ":meta")).isFalse();
    }

    @Test
    void meta가_유실된_세대는_latest_조회에서_ZSET과_함께_정리한다() {
        LocalDateTime generationAt = LocalDateTime.of(2026, 8, 18, 12, 0, 30);
        store.publish(snapshot(generationAt, 1L));
        redisTemplate.delete(generationPrefix(generationAt) + ":meta");

        assertThat(store.findLatestPage(AuctionSort.PRICE_LOW, 1, 16)).isEmpty();
        assertThat(redisTemplate.opsForZSet().score(
                "auction:{down-price}:generations",
                Long.toString(toEpochMilli(generationAt))
        )).isNull();
    }

    private DownPriceSnapshot snapshot(LocalDateTime generationAt, long auctionId) {
        List<AuctionPriceSnapshot> entries = List.of(
                new AuctionPriceSnapshot(auctionId, 100L, 90L)
        );
        return new DownPriceSnapshot(generationAt, entries, entries);
    }

    private List<AuctionPriceSnapshot> snapshots(long idOffset, int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> new AuctionPriceSnapshot(
                        idOffset + index,
                        index * 10L,
                        index * 10L - 1L
                ))
                .toList();
    }

    private String generationPrefix(LocalDateTime generationAt) {
        return "auction:{down-price}:generation:" + toEpochMilli(generationAt);
    }

    private long toEpochMilli(LocalDateTime generationAt) {
        return generationAt.atZone(SEOUL).toInstant().toEpochMilli();
    }
}
