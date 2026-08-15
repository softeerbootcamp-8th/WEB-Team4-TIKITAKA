package com.tikitaka.bidwinback.auction.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DownPriceSnapshotCountCacheTest {

    private static final LocalDateTime SNAPSHOT_AT =
            LocalDateTime.of(2026, 8, 14, 12, 0, 0, 123_000_000);
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String KEY =
            "auction:down-price-snapshot:count:2026-08-14T12:00:00.123";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private LongSupplier loader;

    private DownPriceSnapshotCountCache cache;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cache = new DownPriceSnapshotCountCache(redisTemplate, TTL);
    }

    @Test
    void 캐시_hit이면_DB_loader를_호출하지_않는다() {
        when(valueOperations.get(KEY)).thenReturn("150000");

        long count = cache.getOrLoad(SNAPSHOT_AT, loader);

        assertThat(count).isEqualTo(150_000L);
        verifyNoInteractions(loader);
        verify(valueOperations, never()).set(KEY, "150000", TTL);
    }

    @Test
    void 캐시_miss이면_DB_결과를_TTL과_함께_저장한다() {
        when(valueOperations.get(KEY)).thenReturn(null);
        when(loader.getAsLong()).thenReturn(0L);

        long count = cache.getOrLoad(SNAPSHOT_AT, loader);

        assertThat(count).isZero();
        verify(loader).getAsLong();
        verify(valueOperations).set(KEY, "0", TTL);
    }

    @Test
    void public_put은_세대별_count를_TTL과_함께_저장한다() {
        cache.put(SNAPSHOT_AT, 321L);

        verify(valueOperations).set(KEY, "321", TTL);
    }

    @Test
    void 세대시각이_다르면_서로_다른_키를_사용한다() {
        LocalDateTime nextSnapshotAt = SNAPSHOT_AT.plusMinutes(1);
        String nextKey = "auction:down-price-snapshot:count:2026-08-14T12:01:00.123";
        when(valueOperations.get(KEY)).thenReturn("10");
        when(valueOperations.get(nextKey)).thenReturn("20");

        long first = cache.getOrLoad(SNAPSHOT_AT, loader);
        long second = cache.getOrLoad(nextSnapshotAt, loader);

        assertThat(first).isEqualTo(10L);
        assertThat(second).isEqualTo(20L);
    }

    @Test
    void Redis_조회와_저장이_실패해도_DB_결과로_폴백한다() {
        doThrow(new RuntimeException("redis read failed"))
                .when(valueOperations).get(KEY);
        when(loader.getAsLong()).thenReturn(42L);
        doThrow(new RuntimeException("redis write failed"))
                .when(valueOperations).set(KEY, "42", TTL);

        long count = cache.getOrLoad(SNAPSHOT_AT, loader);

        assertThat(count).isEqualTo(42L);
        verify(loader).getAsLong();
    }

    @Test
    void 잘못된_캐시값은_DB_결과로_교체한다() {
        when(valueOperations.get(KEY)).thenReturn("invalid");
        when(loader.getAsLong()).thenReturn(7L);

        long count = cache.getOrLoad(SNAPSHOT_AT, loader);

        assertThat(count).isEqualTo(7L);
        verify(valueOperations).set(KEY, "7", TTL);
    }

    @Test
    void 같은_세대의_동시_cache_miss는_loader를_한번만_실행한다() throws Exception {
        int requestCount = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch initialReads = new CountDownLatch(requestCount);
        CountDownLatch allowReads = new CountDownLatch(1);
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch allowLoader = new CountDownLatch(1);
        AtomicInteger redisReads = new AtomicInteger();
        AtomicInteger loaderCalls = new AtomicInteger();

        when(valueOperations.get(KEY)).thenAnswer(invocation -> {
            if (redisReads.incrementAndGet() <= requestCount) {
                initialReads.countDown();
                if (!allowReads.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("동시 요청이 Redis miss 확인 단계에 도달하지 못했습니다");
                }
            }
            return null;
        });
        when(loader.getAsLong()).thenAnswer(invocation -> {
            loaderCalls.incrementAndGet();
            loaderStarted.countDown();
            if (!allowLoader.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("single-flight loader가 종료되지 않았습니다");
            }
            return 123L;
        });

        List<Future<Long>> futures = new ArrayList<>(requestCount);
        try (ExecutorService executor = Executors.newFixedThreadPool(requestCount)) {
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("동시 요청이 시작되지 않았습니다");
                    }
                    return cache.getOrLoad(SNAPSHOT_AT, loader);
                }));
            }

            start.countDown();
            assertThat(initialReads.await(5, TimeUnit.SECONDS)).isTrue();
            allowReads.countDown();
            assertThat(loaderStarted.await(5, TimeUnit.SECONDS)).isTrue();
            allowLoader.countDown();

            for (Future<Long> future : futures) {
                assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(123L);
            }
        }

        assertThat(loaderCalls).hasValue(1);
        verify(valueOperations).set(KEY, "123", TTL);
    }
}
