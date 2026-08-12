package com.tikitaka.bidwinback.auction.application;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class BidPriceCacheRedisTest {

    private static final long INITIAL_PRICE = 100_000L;
    private static final AtomicLong AUCTION_IDS = new AtomicLong(9_000_000L);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private BidPriceCache bidPriceCache;
    private long auctionId;
    private String key;

    @BeforeAll
    static void setUpRedis() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                environment("REDIS_HOST", "localhost"),
                Integer.parseInt(environment("REDIS_PORT", "6379"))
        );
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder()
                                .connectTimeout(Duration.ofMillis(500))
                                .build())
                        .build())
                .commandTimeout(Duration.ofMillis(100))
                .build();
        connectionFactory = new LettuceConnectionFactory(configuration, clientConfiguration);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void closeRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        bidPriceCache = new BidPriceCache(redisTemplate);
        auctionId = AUCTION_IDS.incrementAndGet();
        key = key(auctionId);
    }

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(key);
    }

    @Test
    void 캐시가_없으면_저가로_판정하지_않고_값을_만들지_않는다() {
        assertThat(bidPriceCache.isTooLow(auctionId, INITIAL_PRICE)).isFalse();
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    @Test
    void 캐시된_가격_이하만_저가이고_읽기는_값을_변경하지_않는다() {
        initialize();
        String cachedPrice = redisTemplate.opsForValue().get(key);
        long ttlBeforeRead = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);

        assertThat(bidPriceCache.isTooLow(auctionId, INITIAL_PRICE - 1_000L)).isTrue();
        assertThat(bidPriceCache.isTooLow(auctionId, INITIAL_PRICE)).isTrue();
        assertThat(bidPriceCache.isTooLow(auctionId, INITIAL_PRICE + 1_000L)).isFalse();

        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo(cachedPrice);
        assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS))
                .isPositive()
                .isLessThanOrEqualTo(ttlBeforeRead);
    }

    @Test
    void 오염된_캐시는_저가로_판정하지_않는다() {
        redisTemplate.opsForValue().set(key, "not-a-price");

        assertThat(bidPriceCache.isTooLow(auctionId, INITIAL_PRICE)).isFalse();
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("not-a-price");
    }

    @Test
    void 이전_선점_캐시의_가격은_커밋_가격으로_사용하지_않는다() {
        String legacyKey = "auction:" + auctionId + ":price";
        redisTemplate.opsForValue().set(legacyKey, String.valueOf(Long.MAX_VALUE));

        try {
            assertThat(bidPriceCache.isTooLow(auctionId, INITIAL_PRICE)).isFalse();
        } finally {
            redisTemplate.delete(legacyKey);
        }
    }

    @Test
    void 커밋된_가격_갱신은_키를_생성하고_마감_기준_TTL을_설정한다() {
        LocalDateTime endedAt = LocalDateTime.now().plusMinutes(30);

        bidPriceCache.updateCommittedPrice(auctionId, INITIAL_PRICE, endedAt);

        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo(String.valueOf(INITIAL_PRICE));
        long ttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        assertThat(ttl)
                .isGreaterThan(Duration.ofMinutes(89).toMillis())
                .isLessThanOrEqualTo(Duration.ofMinutes(91).toMillis());
    }

    @Test
    void 늦게_도착한_낮은_가격과_같은_가격은_캐시를_후퇴시키지_않는다() {
        LocalDateTime endedAt = LocalDateTime.now().plusMinutes(30);
        long highestPrice = INITIAL_PRICE + 2_000L;

        bidPriceCache.updateCommittedPrice(auctionId, highestPrice, endedAt);
        bidPriceCache.updateCommittedPrice(auctionId, INITIAL_PRICE, endedAt);
        bidPriceCache.updateCommittedPrice(auctionId, highestPrice, endedAt);

        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo(String.valueOf(highestPrice));
        assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)).isPositive();
    }

    @Test
    void 커밋_이벤트가_동시에_역순으로_도착해도_최댓값을_유지한다() throws Exception {
        int eventCount = 200;
        long highestPrice = INITIAL_PRICE + eventCount * 1_000L;
        LocalDateTime endedAt = LocalDateTime.now().plusMinutes(30);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(20)) {
            List<Future<?>> futures = new ArrayList<>(eventCount);
            for (int index = 0; index < eventCount; index++) {
                long eventPrice = index % 2 == 0
                        ? highestPrice
                        : INITIAL_PRICE + (index + 1L) * 1_000L;
                futures.add(executor.submit(() -> {
                    start.await();
                    bidPriceCache.updateCommittedPrice(auctionId, eventPrice, endedAt);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo(String.valueOf(highestPrice));
        assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)).isPositive();
    }

    @Test
    void BIGINT_범위의_가격을_정확하게_비교하고_저장한다() {
        long current = 9_223_372_036_854_774_000L;
        long incoming = 9_223_372_036_854_775_000L;
        LocalDateTime endedAt = LocalDateTime.now().plusMinutes(30);

        bidPriceCache.updateCommittedPrice(auctionId, current, endedAt);
        bidPriceCache.updateCommittedPrice(auctionId, incoming, endedAt);

        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo(String.valueOf(incoming));
        assertThat(bidPriceCache.isTooLow(auctionId, current)).isTrue();
        assertThat(bidPriceCache.isTooLow(auctionId, incoming + 500L)).isFalse();
    }

    @Test
    @Tag("redis-load")
    void 단일_hot_key의_읽기_거절_경로는_초당_천_건_이상을_처리한다() throws Exception {
        initialize();
        int workers = 100;
        int requestsPerWorker = 200;
        int requestCount = workers * requestsPerWorker;
        long[] latencies = new long[requestCount];
        CountDownLatch start = new CountDownLatch(1);
        AtomicLong sequence = new AtomicLong();

        for (int index = 0; index < 1_000; index++) {
            assertThat(bidPriceCache.isTooLow(auctionId, INITIAL_PRICE)).isTrue();
        }

        long startedAt;
        try (ExecutorService executor = Executors.newFixedThreadPool(workers)) {
            List<Future<Integer>> futures = new ArrayList<>(workers);
            for (int worker = 0; worker < workers; worker++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    int lostCount = 0;
                    for (int index = 0; index < requestsPerWorker; index++) {
                        long callStartedAt = System.nanoTime();
                        boolean tooLow = bidPriceCache.isTooLow(auctionId, INITIAL_PRICE);
                        latencies[Math.toIntExact(sequence.getAndIncrement())] =
                                System.nanoTime() - callStartedAt;
                        if (tooLow) {
                            lostCount++;
                        }
                    }
                    return lostCount;
                }));
            }

            startedAt = System.nanoTime();
            start.countDown();
            int lostCount = 0;
            for (Future<Integer> future : futures) {
                lostCount += future.get();
            }
            long elapsedNanos = System.nanoTime() - startedAt;
            double requestsPerSecond = requestCount / (elapsedNanos / 1_000_000_000.0);

            Arrays.sort(latencies);
            long p95Micros = latencies[percentileIndex(requestCount, 0.95)] / 1_000L;
            long p99Micros = latencies[percentileIndex(requestCount, 0.99)] / 1_000L;
            System.out.printf(
                    "Redis hot-key read: %.0f req/s, p95=%dµs, p99=%dµs%n",
                    requestsPerSecond,
                    p95Micros,
                    p99Micros
            );

            assertThat(lostCount).isEqualTo(requestCount);
            assertThat(requestsPerSecond).isGreaterThanOrEqualTo(1_000.0);
        }
    }

    @Test
    @Tag("redis-load")
    void 여러_경매의_역순_커밋_갱신도_최댓값을_유지하며_초당_천_건_이상을_처리한다() throws Exception {
        int workers = 50;
        int requestsPerWorker = 200;
        int requestCount = workers * requestsPerWorker;
        long[] auctionIds = new long[workers];
        long[] latencies = new long[requestCount];
        AtomicLong sequence = new AtomicLong();
        CountDownLatch start = new CountDownLatch(1);

        for (int worker = 0; worker < workers; worker++) {
            auctionIds[worker] = AUCTION_IDS.incrementAndGet();
            bidPriceCache.updateCommittedPrice(
                    auctionIds[worker],
                    INITIAL_PRICE,
                    LocalDateTime.now().plusMinutes(30)
            );
        }

        try {
            long startedAt;
            try (ExecutorService executor = Executors.newFixedThreadPool(workers)) {
                List<Future<Integer>> futures = new ArrayList<>(workers);
                for (int worker = 0; worker < workers; worker++) {
                    long workerAuctionId = auctionIds[worker];
                    futures.add(executor.submit(() -> {
                        start.await();
                        long highestPrice = INITIAL_PRICE + requestsPerWorker * 1_000L;
                        for (int index = 0; index < requestsPerWorker; index++) {
                            long price = index % 2 == 0
                                    ? highestPrice
                                    : INITIAL_PRICE + (index + 1L) * 1_000L;
                            long callStartedAt = System.nanoTime();
                            bidPriceCache.updateCommittedPrice(
                                    workerAuctionId,
                                    price,
                                    LocalDateTime.now().plusMinutes(30)
                            );
                            latencies[Math.toIntExact(sequence.getAndIncrement())] =
                                    System.nanoTime() - callStartedAt;
                        }
                        return 0;
                    }));
                }

                startedAt = System.nanoTime();
                start.countDown();
                for (Future<Integer> future : futures) {
                    future.get();
                }
                long elapsedNanos = System.nanoTime() - startedAt;
                double requestsPerSecond = requestCount / (elapsedNanos / 1_000_000_000.0);

                Arrays.sort(latencies);
                long p95Micros = latencies[percentileIndex(requestCount, 0.95)] / 1_000L;
                long p99Micros = latencies[percentileIndex(requestCount, 0.99)] / 1_000L;
                System.out.printf(
                        "Redis write Lua: %.0f req/s, p95=%dµs, p99=%dµs%n",
                        requestsPerSecond,
                        p95Micros,
                        p99Micros
                );

                assertThat(requestsPerSecond).isGreaterThanOrEqualTo(1_000.0);
            }

            long expectedFinalPrice = INITIAL_PRICE + requestsPerWorker * 1_000L;
            for (long workerAuctionId : auctionIds) {
                String workerKey = key(workerAuctionId);
                assertThat(redisTemplate.opsForValue().get(workerKey))
                        .isEqualTo(String.valueOf(expectedFinalPrice));
                assertThat(redisTemplate.getExpire(workerKey, TimeUnit.MILLISECONDS)).isPositive();
            }
        } finally {
            List<String> keys = Arrays.stream(auctionIds)
                    .mapToObj(BidPriceCacheRedisTest::key)
                    .toList();
            redisTemplate.delete(keys);
        }
    }

    private void initialize() {
        bidPriceCache.updateCommittedPrice(
                auctionId,
                INITIAL_PRICE,
                LocalDateTime.now().plusMinutes(30)
        );
    }

    private static int percentileIndex(int size, double percentile) {
        return (int) Math.ceil(size * percentile) - 1;
    }

    private static String environment(String name, String defaultValue) {
        return System.getenv().getOrDefault(name, defaultValue);
    }

    private static String key(long auctionId) {
        return "auction:" + auctionId + ":committed-price";
    }
}
