package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
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

    private DownPriceSnapshotCache cache;

    @BeforeEach
    void setUp() {
        cache = new DownPriceSnapshotCache(redisTemplate, TTL);
    }

    @Test
    void 조회할_세대가_없으면_캐시_미스로_처리한다() {
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
    }

    @Test
    void 세대의_count가_없으면_캐시_미스로_처리한다() {
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
    }

    @Test
    void 저장된_목록_길이가_요청한_길이와_다르면_캐시_미스로_처리한다() {
        DownPriceSnapshotCache.Metadata metadata =
                new DownPriceSnapshotCache.Metadata(SNAPSHOT_AT, 2L);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(anyString(), eq(0L), eq(1L)))
                .thenReturn(List.of("1:100"));

        Optional<List<AuctionPriceSnapshot>> result =
                cache.findPage(metadata, AuctionSort.PRICE_LOW, 0L, 2);

        assertThat(result).isEmpty();
    }

    @Test
    void Redis_조회_장애는_캐시_미스로_처리한다() {
        when(redisTemplate.opsForZSet()).thenThrow(
                new RedisConnectionFailureException("Redis 장애")
        );

        Optional<DownPriceSnapshotCache.Metadata> result =
                cache.findLatestAtNotAfter(SNAPSHOT_AT);

        assertThat(result).isEmpty();
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

}
