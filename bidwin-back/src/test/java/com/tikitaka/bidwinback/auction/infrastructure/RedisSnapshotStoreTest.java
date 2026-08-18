package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.DownPriceSnapshot;
import com.tikitaka.bidwinback.auction.application.DownPriceSnapshotMetrics;
import com.tikitaka.bidwinback.auction.application.SnapshotGenerationPage;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class RedisSnapshotStoreTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime GENERATION_AT =
            LocalDateTime.of(2026, 8, 18, 12, 0, 30);

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisSnapshotStore store;

    @BeforeEach
    void setUp() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DownPriceSnapshotMetrics metrics = new DownPriceSnapshotMetrics(registry);
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

    @Test
    void LOW_HIGH_목록을_세대별_키에_세_필드_형식으로_발행한다() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn(1L);
        DownPriceSnapshot snapshot = new DownPriceSnapshot(
                GENERATION_AT,
                List.of(new AuctionPriceSnapshot(1L, 100L, 90L)),
                List.of(new AuctionPriceSnapshot(1L, 100L, 90L))
        );

        store.publish(snapshot);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(
                any(RedisScript.class),
                keys.capture(),
                arguments.capture()
        );
        String generation = Long.toString(toEpochMilli(GENERATION_AT));
        assertThat(keys.getValue()).containsExactly(
                "auction:{down-price}:generations",
                "auction:{down-price}:generation:" + generation + ":meta",
                "auction:{down-price}:generation:" + generation + ":low",
                "auction:{down-price}:generation:" + generation + ":high"
        );
        assertThat(arguments.getValue()).contains(
                generation,
                "600000",
                "1:100:90"
        );
    }

    @Test
    void exact_세대의_요청한_16건만_저장된_순서대로_복원한다() {
        String generation = Long.toString(toEpochMilli(GENERATION_AT));
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn(List.of(
                generation,
                "18",
                "18:200:190",
                "17:210:195"
        ));

        SnapshotGenerationPage page = store.findExactPage(
                GENERATION_AT,
                AuctionSort.PRICE_LOW,
                2,
                16
        ).orElseThrow();

        assertThat(page.generationAt()).isEqualTo(GENERATION_AT);
        assertThat(page.totalCount()).isEqualTo(18);
        assertThat(page.entries()).containsExactly(
                new AuctionPriceSnapshot(18L, 200L, 190L),
                new AuctionPriceSnapshot(17L, 210L, 195L)
        );
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(SEOUL).toInstant().toEpochMilli();
    }
}
