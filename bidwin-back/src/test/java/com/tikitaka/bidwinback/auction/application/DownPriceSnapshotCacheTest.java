package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DownPriceSnapshotCacheTest {

    private static final String LOOKUP_METRIC = "auction.down.price.snapshot.lookup";
    private static final String PUBLISH_METRIC = "auction.down.price.snapshot.publish";
    private static final String STALENESS_METRIC = "auction.down.price.snapshot.staleness";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final LocalDateTime SNAPSHOT_AT =
            LocalDateTime.of(2026, 8, 15, 10, 0, 0, 123_000_000);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ListOperations<String, String> listOperations;

    private SimpleMeterRegistry meterRegistry;
    private DownPriceSnapshotCache cache;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        cache = new DownPriceSnapshotCache(redisTemplate, TTL, meterRegistry);
    }

    @Test
    void 조회할_세대가_없으면_no_generation_miss를_한번_기록한다() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRangeByScore(
                anyString(),
                anyDouble(),
                anyDouble(),
                anyLong(),
                anyLong()
        )).thenReturn(Set.of());

        Optional<DownPriceSnapshotCache.Metadata> result =
                cache.findLatestAtNotAfter(SNAPSHOT_AT);

        assertThat(result).isEmpty();
        assertSingleLookup("miss", "no_generation");
    }

    @Test
    void 세대의_count가_없으면_no_count_miss를_한번_기록한다() {
        String generation = Long.toString(toEpochMilli(SNAPSHOT_AT));
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(zSetOperations.reverseRangeByScore(
                anyString(),
                anyDouble(),
                anyDouble(),
                anyLong(),
                anyLong()
        )).thenReturn(Set.of(generation));

        Optional<DownPriceSnapshotCache.Metadata> result =
                cache.findLatestAtNotAfter(SNAPSHOT_AT);

        assertThat(result).isEmpty();
        assertSingleLookup("miss", "no_count");
    }

    @Test
    void 요청_asOf_이하의_가장_최신_세대와_같은_세대의_count를_읽는다() {
        LocalDateTime asOf = SNAPSHOT_AT.plusSeconds(30);
        String generation = Long.toString(toEpochMilli(SNAPSHOT_AT));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRangeByScore(
                anyString(),
                eq(0D),
                eq((double) toEpochMilli(asOf)),
                eq(0L),
                eq(1L)
        )).thenReturn(Set.of(generation));
        when(valueOperations.get(anyString())).thenReturn("2000");

        Optional<DownPriceSnapshotCache.Metadata> result =
                cache.findLatestAtNotAfter(asOf);

        assertThat(result).contains(new DownPriceSnapshotCache.Metadata(SNAPSHOT_AT, 2_000L));
        verify(valueOperations).get(
                "auction:{down-price-snapshot}:generation:" + generation + ":count"
        );
    }

    @Test
    void 낮은순과_높은순_목록을_저장된_순서대로_읽는다() {
        String generation = Long.toString(toEpochMilli(SNAPSHOT_AT));
        DownPriceSnapshotCache.Metadata metadata =
                new DownPriceSnapshotCache.Metadata(SNAPSHOT_AT, 3L);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(
                "auction:{down-price-snapshot}:generation:" + generation + ":low",
                0,
                1
        )).thenReturn(List.of("3:100", "2:100"));
        when(listOperations.range(
                "auction:{down-price-snapshot}:generation:" + generation + ":high",
                0,
                1
        )).thenReturn(List.of("1:300", "2:100"));

        Optional<List<AuctionPriceSnapshot>> low =
                cache.findPage(metadata, AuctionSort.PRICE_LOW, 0, 2);
        Optional<List<AuctionPriceSnapshot>> high =
                cache.findPage(metadata, AuctionSort.PRICE_HIGH, 0, 2);

        assertThat(low).contains(List.of(
                new AuctionPriceSnapshot(3L, 100L),
                new AuctionPriceSnapshot(2L, 100L)
        ));
        assertThat(high).contains(List.of(
                new AuctionPriceSnapshot(1L, 300L),
                new AuctionPriceSnapshot(2L, 100L)
        ));
    }

    @Test
    void 캐시된_1600개_범위를_넘는_페이지는_기존_조회로_폴백시킨다() {
        DownPriceSnapshotCache.Metadata metadata =
                new DownPriceSnapshotCache.Metadata(SNAPSHOT_AT, 2_000L);

        Optional<List<AuctionPriceSnapshot>> result =
                cache.findPage(metadata, AuctionSort.PRICE_LOW, 1_550L, 100);

        assertThat(result).isEmpty();
        verify(redisTemplate, never()).opsForList();
        assertSingleLookup("miss", "beyond_cache");
    }

    @Test
    void 저장된_목록_길이가_요청한_길이와_다르면_length_mismatch_miss를_기록한다() {
        DownPriceSnapshotCache.Metadata metadata =
                new DownPriceSnapshotCache.Metadata(SNAPSHOT_AT, 2L);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(anyString(), eq(0L), eq(1L)))
                .thenReturn(List.of("1:100"));

        Optional<List<AuctionPriceSnapshot>> result =
                cache.findPage(metadata, AuctionSort.PRICE_LOW, 0L, 2);

        assertThat(result).isEmpty();
        assertSingleLookup("miss", "length_mismatch");
    }

    @Test
    void 전체_건수가_0이면_빈_페이지를_hit으로_한번_기록한다() {
        DownPriceSnapshotCache.Metadata metadata =
                new DownPriceSnapshotCache.Metadata(SNAPSHOT_AT, 0L);

        Optional<List<AuctionPriceSnapshot>> result =
                cache.findPage(metadata, AuctionSort.PRICE_LOW, 0L, 16);

        assertThat(result).contains(List.of());
        assertSingleLookup("hit", "none");
    }

    @Test
    void Redis_조회_장애는_캐시_미스로_처리한다() {
        when(redisTemplate.opsForZSet()).thenThrow(
                new RedisConnectionFailureException("Redis 장애")
        );

        Optional<DownPriceSnapshotCache.Metadata> result =
                cache.findLatestAtNotAfter(SNAPSHOT_AT);

        assertThat(result).isEmpty();
        assertSingleLookup("miss", "redis_error");
    }

    @Test
    void count와_양방향_목록과_세대_인덱스를_10분_TTL로_한번에_발행한다() {
        DownPriceSnapshot snapshot = new DownPriceSnapshot(
                SNAPSHOT_AT,
                2L,
                List.of(
                        new AuctionPriceSnapshot(2L, 100L),
                        new AuctionPriceSnapshot(1L, 200L)
                ),
                List.of(
                        new AuctionPriceSnapshot(1L, 200L),
                        new AuctionPriceSnapshot(2L, 100L)
                )
        );
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn(1L);

        assertThat(meterRegistry.get(STALENESS_METRIC).gauge().value()).isNaN();

        cache.publish(snapshot);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(
                any(RedisScript.class),
                keysCaptor.capture(),
                argumentsCaptor.capture()
        );
        String generation = Long.toString(toEpochMilli(SNAPSHOT_AT));
        assertThat(keysCaptor.getValue()).containsExactly(
                "auction:{down-price-snapshot}:generation:" + generation + ":count",
                "auction:{down-price-snapshot}:generation:" + generation + ":low",
                "auction:{down-price-snapshot}:generation:" + generation + ":high",
                "auction:{down-price-snapshot}:generations"
        );
        assertThat(argumentsCaptor.getValue()).containsExactly(
                "2",
                "600000",
                "2",
                "2:100",
                "1:200",
                "2",
                "1:200",
                "2:100",
                generation,
                Long.toString(toEpochMilli(SNAPSHOT_AT) - TTL.toMillis())
        );
        assertThat(publishCount("success")).isEqualTo(1D);
        assertThat(publishCount("failure")).isZero();
        assertThat(meterRegistry.get(STALENESS_METRIC).gauge().value()).isFinite();
    }

    @Test
    void 발행_실패를_기록하고_발행_전_staleness를_갱신하지_않는다() {
        DownPriceSnapshot snapshot = emptySnapshot(SNAPSHOT_AT);
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn(0L);

        assertThatThrownBy(() -> cache.publish(snapshot))
                .isInstanceOf(IllegalStateException.class);

        assertThat(publishCount("success")).isZero();
        assertThat(publishCount("failure")).isEqualTo(1D);
        assertThat(meterRegistry.get(STALENESS_METRIC).gauge().value()).isNaN();
    }

    @Test
    void Redis_예외로_발행에_실패해도_failure를_기록한다() {
        DownPriceSnapshot snapshot = emptySnapshot(SNAPSHOT_AT);
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenThrow(new RedisConnectionFailureException("Redis 장애"));

        assertThatThrownBy(() -> cache.publish(snapshot))
                .isInstanceOf(RedisConnectionFailureException.class);

        assertThat(publishCount("success")).isZero();
        assertThat(publishCount("failure")).isEqualTo(1D);
        assertThat(meterRegistry.get(STALENESS_METRIC).gauge().value()).isNaN();
    }

    @Test
    void 발행에_실패해도_마지막_성공_시각을_갱신하지_않는다() throws InterruptedException {
        DownPriceSnapshot snapshot = emptySnapshot(SNAPSHOT_AT);
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn(1L, 0L);
        cache.publish(snapshot);
        Thread.sleep(20L);
        double beforeFailure = meterRegistry.get(STALENESS_METRIC).gauge().value();

        assertThatThrownBy(() -> cache.publish(snapshot))
                .isInstanceOf(IllegalStateException.class);

        double afterFailure = meterRegistry.get(STALENESS_METRIC).gauge().value();
        assertThat(beforeFailure).isGreaterThanOrEqualTo(10D);
        assertThat(afterFailure).isGreaterThanOrEqualTo(beforeFailure);
    }

    @Test
    void 스케줄러_락은_50초_TTL로_한_인스턴스만_획득한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("owner"), eq(Duration.ofSeconds(50))))
                .thenReturn(true);

        boolean acquired = cache.tryAcquireCaptureLock("owner");

        assertThat(acquired).isTrue();
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(SEOUL).toInstant().toEpochMilli();
    }

    private DownPriceSnapshot emptySnapshot(LocalDateTime snapshotAt) {
        return new DownPriceSnapshot(snapshotAt, 0L, List.of(), List.of());
    }

    private void assertSingleLookup(String result, String reason) {
        assertThat(meterRegistry.get(LOOKUP_METRIC)
                .tags("result", result, "reason", reason)
                .counter()
                .count()).isEqualTo(1D);
        double total = meterRegistry.find(LOOKUP_METRIC)
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
        assertThat(total).isEqualTo(1D);
    }

    private double publishCount(String result) {
        return meterRegistry.get(PUBLISH_METRIC)
                .tag("result", result)
                .counter()
                .count();
    }
}
