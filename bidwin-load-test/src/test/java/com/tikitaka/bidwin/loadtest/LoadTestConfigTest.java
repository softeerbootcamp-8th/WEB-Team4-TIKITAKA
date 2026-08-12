package com.tikitaka.bidwin.loadtest;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoadTestConfigTest {

    @Test
    void 환경변수로_부하_형상과_구독_대상을_구성한다() {
        // given
        Map<String, String> environment = requiredEnvironment();
        environment.put("SSE_MAX_SUBSCRIBERS", "10");
        environment.put("SSE_DURATION", "PT1M");
        environment.put("SSE_RAMP_UP", "PT10S");
        environment.put("SSE_RAMP_DOWN", "PT20S");
        environment.put("SSE_SLOW_CONSUMER_RATIO", "0.3");
        environment.put("BID_INTERVAL", "PT0.5S");
        environment.put("AUCTION_IDS", "1, 2, 1");

        // when
        LoadTestConfig config = LoadTestConfig.from(environment);

        // then
        assertEquals(10, config.maxSubscribers());
        assertEquals(7, config.normalSubscribers());
        assertEquals(3, config.slowSubscribers());
        assertEquals(List.of(1L, 2L), config.auctionIds());
        assertEquals(Duration.ofMillis(500), config.bidInterval());
        assertEquals(Duration.ofSeconds(10), config.rampUp());
        assertEquals(Duration.ofSeconds(20), config.rampDown());
        assertEquals(Duration.ofSeconds(90), config.totalDuration());
        assertEquals(Duration.ofSeconds(70), config.subscriberDuration(0, 3));
        assertEquals(Duration.ofSeconds(80), config.subscriberDuration(2, 3));
    }

    @Test
    void 점진_시간이_0이면_모든_구독자가_같은_시간_동안_연결된다() {
        // given
        Map<String, String> environment = requiredEnvironment();
        environment.put("SSE_MAX_SUBSCRIBERS", "2");
        environment.put("SSE_DURATION", "PT30S");
        environment.put("SSE_RAMP_UP", "PT0S");
        environment.put("SSE_RAMP_DOWN", "PT0S");

        // when
        LoadTestConfig config = LoadTestConfig.from(environment);

        // then
        assertEquals(Duration.ofSeconds(30), config.subscriberDuration(0, 2));
        assertEquals(Duration.ofSeconds(30), config.subscriberDuration(1, 2));
    }

    @Test
    void 느린_소비자_비율이_범위를_벗어나면_실행을_거부한다() {
        // given
        Map<String, String> environment = requiredEnvironment();
        environment.put("SSE_SLOW_CONSUMER_RATIO", "1.1");

        // when
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> LoadTestConfig.from(environment)
        );

        // then
        assertEquals(
                "SSE_SLOW_CONSUMER_RATIO는 0 이상 1 이하여야 합니다.",
                exception.getMessage()
        );
    }

    @Test
    void 느린_소비자_비율이_유한한_수가_아니면_실행을_거부한다() {
        // given
        Map<String, String> environment = requiredEnvironment();
        environment.put("SSE_SLOW_CONSUMER_RATIO", "NaN");

        // when
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> LoadTestConfig.from(environment)
        );

        // then
        assertEquals(
                "SSE_SLOW_CONSUMER_RATIO는 0 이상 1 이하여야 합니다.",
                exception.getMessage()
        );
    }

    @Test
    void 입찰_계정이_없으면_실행을_거부한다() {
        // given
        Map<String, String> environment = requiredEnvironment();
        environment.remove("BID_EMAIL");

        // when
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> LoadTestConfig.from(environment)
        );

        // then
        assertEquals("BID_EMAIL 환경변수가 필요합니다.", exception.getMessage());
    }

    @Test
    void 구독_대상에_양수가_아닌_ID가_있으면_실행을_거부한다() {
        // given
        Map<String, String> environment = requiredEnvironment();
        environment.put("AUCTION_IDS", "1,0");

        // when
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> LoadTestConfig.from(environment)
        );

        // then
        assertEquals(
                "AUCTION_IDS는 쉼표로 구분한 양수 ID여야 합니다.",
                exception.getMessage()
        );
    }

    private Map<String, String> requiredEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("AUCTION_IDS", "1");
        environment.put("BID_EMAIL", "load@example.com");
        environment.put("BID_PASSWORD", "password");
        return environment;
    }
}
