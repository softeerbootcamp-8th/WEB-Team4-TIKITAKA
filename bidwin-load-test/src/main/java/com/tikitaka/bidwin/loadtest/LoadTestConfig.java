package com.tikitaka.bidwin.loadtest;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public record LoadTestConfig(
        int maxSubscribers,
        Duration duration,
        Duration rampUp,
        Duration rampDown,
        double slowConsumerRatio,
        Duration bidInterval,
        List<Long> auctionIds,
        String bidEmail,
        String bidPassword,
        String baseUrl,
        String slowBaseUrl,
        int metricsPort,
        int eventBufferSize
) {

    public static LoadTestConfig fromEnvironment() {
        return from(System.getenv());
    }

    static LoadTestConfig from(Map<String, String> environment) {
        int maxSubscribers = integer(environment, "SSE_MAX_SUBSCRIBERS", "100");
        Duration duration = duration(environment, "SSE_DURATION", "PT5M");
        Duration rampUp = duration(environment, "SSE_RAMP_UP", "PT30S");
        Duration rampDown = duration(environment, "SSE_RAMP_DOWN", "PT30S");
        double slowConsumerRatio = decimal(
                environment,
                "SSE_SLOW_CONSUMER_RATIO",
                "0"
        );
        Duration bidInterval = duration(environment, "BID_INTERVAL", "PT1S");
        List<Long> auctionIds = auctionIds(required(environment, "AUCTION_IDS"));
        String bidEmail = required(environment, "BID_EMAIL");
        String bidPassword = required(environment, "BID_PASSWORD");
        String baseUrl = url(environment, "BASE_URL", "http://localhost:8080");
        String slowBaseUrl = url(
                environment,
                "SLOW_BASE_URL",
                "http://localhost:8081"
        );
        int metricsPort = integer(environment, "SSE_METRICS_PORT", "9101");
        int eventBufferSize = integer(environment, "SSE_EVENT_BUFFER_SIZE", "1000");

        if (maxSubscribers <= 0) {
            throw new IllegalArgumentException("SSE_MAX_SUBSCRIBERS는 양수여야 합니다.");
        }
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("SSE_DURATION은 양수여야 합니다.");
        }
        if (rampUp.isNegative() || rampDown.isNegative()) {
            throw new IllegalArgumentException("점진 시간은 음수일 수 없습니다.");
        }
        if (!Double.isFinite(slowConsumerRatio)
                || slowConsumerRatio < 0
                || slowConsumerRatio > 1) {
            throw new IllegalArgumentException(
                    "SSE_SLOW_CONSUMER_RATIO는 0 이상 1 이하여야 합니다."
            );
        }
        if (bidInterval.isZero() || bidInterval.isNegative()) {
            throw new IllegalArgumentException("BID_INTERVAL은 양수여야 합니다.");
        }
        if (metricsPort < 1 || metricsPort > 65_535) {
            throw new IllegalArgumentException("SSE_METRICS_PORT 범위가 올바르지 않습니다.");
        }
        if (eventBufferSize <= 0) {
            throw new IllegalArgumentException("SSE_EVENT_BUFFER_SIZE는 양수여야 합니다.");
        }

        return new LoadTestConfig(
                maxSubscribers,
                duration,
                rampUp,
                rampDown,
                slowConsumerRatio,
                bidInterval,
                auctionIds,
                bidEmail,
                bidPassword,
                baseUrl,
                slowBaseUrl,
                metricsPort,
                eventBufferSize
        );
    }

    public int slowSubscribers() {
        return (int) Math.round(maxSubscribers * slowConsumerRatio);
    }

    public int normalSubscribers() {
        return maxSubscribers - slowSubscribers();
    }

    public Duration totalDuration() {
        return rampUp.plus(duration).plus(rampDown);
    }

    /** 각 연결의 종료 시각을 plateau 종료부터 ramp-down 종료까지 균등하게 배치한다. */
    public Duration subscriberDuration(int index, int populationSize) {
        if (index < 0 || index >= populationSize) {
            throw new IllegalArgumentException("구독자 순번이 범위를 벗어났습니다.");
        }
        double startFraction = populationSize == 1
                ? 0
                : (double) index / (populationSize - 1);
        double stopFraction = populationSize == 1
                ? 1
                : startFraction;
        Duration untilPlateau = rampUp.minus(scale(rampUp, startFraction));
        return untilPlateau.plus(duration).plus(scale(rampDown, stopFraction));
    }

    private static Duration scale(Duration value, double ratio) {
        return Duration.ofNanos(Math.round(value.toNanos() * ratio));
    }

    private static List<Long> auctionIds(String raw) {
        try {
            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .map(Long::parseLong)
                    .forEach(id -> {
                        if (id <= 0) {
                            throw new IllegalArgumentException();
                        }
                        ids.add(id);
                    });
            if (ids.isEmpty()) {
                throw new IllegalArgumentException();
            }
            return List.copyOf(ids);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "AUCTION_IDS는 쉼표로 구분한 양수 ID여야 합니다.",
                    exception
            );
        }
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 환경변수가 필요합니다.");
        }
        return value;
    }

    private static String value(
            Map<String, String> environment,
            String name,
            String defaultValue
    ) {
        String value = environment.get(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static int integer(
            Map<String, String> environment,
            String name,
            String defaultValue
    ) {
        try {
            return Integer.parseInt(value(environment, name, defaultValue));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + "는 정수여야 합니다.", exception);
        }
    }

    private static double decimal(
            Map<String, String> environment,
            String name,
            String defaultValue
    ) {
        try {
            return Double.parseDouble(value(environment, name, defaultValue));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + "는 숫자여야 합니다.", exception);
        }
    }

    private static Duration duration(
            Map<String, String> environment,
            String name,
            String defaultValue
    ) {
        try {
            return Duration.parse(value(environment, name, defaultValue));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    name + "는 ISO-8601 기간이어야 합니다. 예: PT30S, PT5M",
                    exception
            );
        }
    }

    private static String url(
            Map<String, String> environment,
            String name,
            String defaultValue
    ) {
        String value = value(environment, name, defaultValue);
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " URL이 올바르지 않습니다.", exception);
        }
        if (uri.getHost() == null
                || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
            throw new IllegalArgumentException(name + "는 http(s) URL이어야 합니다.");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
