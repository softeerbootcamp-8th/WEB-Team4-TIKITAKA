package com.tikitaka.bidwinback.auction.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionListCountCacheTest {

    private static final Duration TTL = Duration.ofMinutes(2);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AuctionListCountCache countCache;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        countCache = new AuctionListCountCache(redisTemplate, TTL);
    }

    @ParameterizedTest
    @CsvSource({
            "ALL,auction:list:count:ALL,11",
            "UP,auction:list:count:UP,12",
            "DOWN,auction:list:count:DOWN,13"
    })
    void scope별_키에서_정상_count를_조회한다(
            AuctionListCountScope scope,
            String key,
            long count
    ) {
        when(valueOperations.get(key)).thenReturn(String.valueOf(count));

        assertThat(countCache.find(scope)).hasValue(count);
    }

    @Test
    void count_0도_정상_hit로_처리한다() {
        when(valueOperations.get("auction:list:count:ALL")).thenReturn("0");

        assertThat(countCache.find(AuctionListCountScope.ALL)).hasValue(0L);
    }

    @Test
    void 키가_없으면_miss로_처리한다() {
        when(valueOperations.get("auction:list:count:ALL")).thenReturn(null);

        assertThat(countCache.find(AuctionListCountScope.ALL)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "1.0", "+1", "9223372036854775808"})
    void 올바른_10진수_long이_아닌_값은_miss로_처리한다(String value) {
        when(valueOperations.get("auction:list:count:ALL")).thenReturn(value);

        assertThat(countCache.find(AuctionListCountScope.ALL)).isEmpty();
    }

    @Test
    void 음수는_miss로_처리한다() {
        when(valueOperations.get("auction:list:count:ALL")).thenReturn("-1");

        assertThat(countCache.find(AuctionListCountScope.ALL)).isEmpty();
    }

    @Test
    void Redis_조회_예외는_miss로_처리한다() {
        when(valueOperations.get("auction:list:count:ALL"))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertThat(countCache.find(AuctionListCountScope.ALL)).isEmpty();
    }

    @Test
    void 세_scope의_count를_TTL과_함께_저장한다() {
        countCache.publish(new AuctionListCounts(30L, 10L, 20L));

        verify(valueOperations).set("auction:list:count:ALL", "30", TTL);
        verify(valueOperations).set("auction:list:count:UP", "10", TTL);
        verify(valueOperations).set("auction:list:count:DOWN", "20", TTL);
    }

    @Test
    void Redis_publish_예외는_호출자에게_전파한다() {
        doThrow(new IllegalStateException("redis unavailable"))
                .when(valueOperations)
                .set("auction:list:count:ALL", "30", TTL);

        assertThatThrownBy(() -> countCache.publish(new AuctionListCounts(30L, 10L, 20L)))
                .isInstanceOf(IllegalStateException.class);
    }
}
