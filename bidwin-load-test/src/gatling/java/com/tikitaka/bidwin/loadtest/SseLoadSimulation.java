package com.tikitaka.bidwin.loadtest;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.pace;
import static io.gatling.javaapi.core.CoreDsl.pause;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.sse;
import static io.gatling.javaapi.http.HttpDsl.status;

public final class SseLoadSimulation extends io.gatling.javaapi.core.Simulation {

    private static final long BID_UNIT = 1_000L;
    private static final Duration INBOUND_DRAIN_INTERVAL = Duration.ofMillis(100);

    private final LoadTestConfig config = LoadTestConfig.fromEnvironment();
    private final SseClientMetrics metrics = new SseClientMetrics();
    private final ConcurrentHashMap<Long, AtomicLong> prices = new ConcurrentHashMap<>();
    private final AtomicInteger bidTargetIndex = new AtomicInteger();

    private final HttpProtocolBuilder normalProtocol = http
            .baseUrl(config.baseUrl())
            .sseUnmatchedInboundMessageBufferSize(config.eventBufferSize());
    private final HttpProtocolBuilder slowProtocol = http
            .baseUrl(config.slowBaseUrl())
            .sseUnmatchedInboundMessageBufferSize(config.eventBufferSize());

    public SseLoadSimulation() {
        List<PopulationBuilder> populations = new ArrayList<>();
        if (config.normalSubscribers() > 0) {
            populations.add(subscriberPopulation(
                    "일반 SSE 구독자",
                    SseClientMetrics.Consumer.NORMAL,
                    config.normalSubscribers(),
                    normalProtocol
            ));
        }
        if (config.slowSubscribers() > 0) {
            populations.add(subscriberPopulation(
                    "느린 SSE 구독자",
                    SseClientMetrics.Consumer.SLOW,
                    config.slowSubscribers(),
                    slowProtocol
            ));
        }
        populations.add(bidder().injectOpen(atOnceUsers(1)).protocols(normalProtocol));

        setUp(populations.toArray(PopulationBuilder[]::new))
                .maxDuration(config.totalDuration().plus(Duration.ofSeconds(30)));
    }

    @Override
    public void before() {
        metrics.startServer(config.metricsPort());
        System.out.printf(
                "SSE load test: subscribers=%d (normal=%d, slow=%d), auctions=%s, "
                        + "rampUp=%s, duration=%s, rampDown=%s%n",
                config.maxSubscribers(),
                config.normalSubscribers(),
                config.slowSubscribers(),
                config.auctionIds(),
                config.rampUp(),
                config.duration(),
                config.rampDown()
        );
    }

    @Override
    public void after() {
        metrics.stopServer();
    }

    private PopulationBuilder subscriberPopulation(
            String name,
            SseClientMetrics.Consumer consumer,
            int subscribers,
            HttpProtocolBuilder protocol
    ) {
        AtomicInteger index = new AtomicInteger();
        ScenarioBuilder scenario = scenario(name)
                .exec(session -> session
                        .set("subscriberState", new SseClientMetrics.SubscriberState())
                        .set(
                                "subscriberDuration",
                                config.subscriberDuration(index.getAndIncrement(), subscribers)
                        ))
                .exec(sse("SSE 연결").get(ssePath()))
                .asLongAsDuring(
                        session -> true,
                        session -> session.get("subscriberDuration"),
                        "sseDrainLoop",
                        true
                ).on(
                        pause(INBOUND_DRAIN_INTERVAL),
                        drainInboundMessages(consumer)
                )
                .exec(drainInboundMessages(consumer))
                .exec(sse("SSE 연결 종료").close());

        PopulationBuilder population = config.rampUp().isZero()
                ? scenario.injectOpen(atOnceUsers(subscribers))
                : scenario.injectOpen(rampUsers(subscribers).during(config.rampUp()));
        return population.protocols(protocol);
    }

    private ChainBuilder drainInboundMessages(SseClientMetrics.Consumer consumer) {
        return exec(sse.processUnmatchedMessages((messages, session) -> {
            SseClientMetrics.SubscriberState state = session.get("subscriberState");
            messages.forEach(message -> metrics.recordInbound(
                    consumer,
                    state,
                    message.message(),
                    message.timestamp()
            ));
            return session;
        }));
    }

    private ScenarioBuilder bidder() {
        return scenario("단일 계정 입찰자")
                .exec(http("입찰 계정 로그인")
                        .post("/api/v1/auth/login")
                        .header("Content-Type", "application/json")
                        .body(io.gatling.javaapi.core.CoreDsl.StringBody(loginBody()))
                        .check(status().is(200)))
                .exec(initializePrices())
                .during(config.totalDuration()).on(
                        pace(config.bidInterval()),
                        exec(session -> session.set(
                                "auctionId",
                                config.auctionIds().get(Math.floorMod(
                                        bidTargetIndex.getAndIncrement(),
                                        config.auctionIds().size()
                                ))
                        )),
                        exec(http("입찰")
                                .post(session -> "/api/v1/auctions/up/"
                                        + session.getLong("auctionId") + "/bids")
                                .header("Content-Type", "application/json")
                                .body(io.gatling.javaapi.core.CoreDsl.StringBody(session -> {
                                    long price = prices.get(session.getLong("auctionId"))
                                            .addAndGet(BID_UNIT);
                                    return "{\"price\":" + price
                                            + ",\"bidType\":\"OPEN\"}";
                                }))
                                .check(
                                        status().is(201),
                                        jsonPath("$.data.bidId").ofLong().saveAs("bidId")
                                )),
                        exec(session -> {
                            if (session.contains("bidId")) {
                                metrics.recordBidSucceeded(
                                        session.getLong("bidId"),
                                        System.currentTimeMillis()
                                );
                            }
                            return session.remove("bidId");
                        })
                );
    }

    private ChainBuilder initializePrices() {
        ChainBuilder chain = exec(session -> session);
        for (long auctionId : config.auctionIds()) {
            String priceKey = "currentPrice-" + auctionId;
            chain = chain
                    .exec(http("경매 현재가 조회")
                            .get("/api/v1/auctions/" + auctionId)
                            .check(
                                    status().is(200),
                                    jsonPath("$.data.auctionType").is("UP"),
                                    jsonPath("$.data.currentPrice").ofLong().saveAs(priceKey)
                            ))
                    .exec(session -> {
                        prices.put(auctionId, new AtomicLong(session.getLong(priceKey)));
                        return session;
                    });
        }
        return chain;
    }

    private String ssePath() {
        if (config.auctionIds().size() == 1) {
            return "/api/v1/auctions/" + config.auctionIds().get(0) + "/events";
        }
        return "/api/v1/auctions/events?" + config.auctionIds().stream()
                .map(id -> "auctionIds=" + id)
                .collect(Collectors.joining("&"));
    }

    private String loginBody() {
        return "{\"email\":" + quote(config.bidEmail())
                + ",\"password\":" + quote(config.bidPassword()) + "}";
    }

    private String quote(String value) {
        StringBuilder quoted = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\b' -> quoted.append("\\b");
                case '\f' -> quoted.append("\\f");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (character < 0x20) {
                        quoted.append(String.format("\\u%04x", (int) character));
                    } else {
                        quoted.append(character);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }
}
