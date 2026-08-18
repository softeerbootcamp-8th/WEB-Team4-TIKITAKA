package com.tikitaka.bidwinback.auction.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class DownPriceSnapshotMetrics {

    private static final long NO_GENERATION = Long.MIN_VALUE;

    private final MeterRegistry meterRegistry;
    private final Timer buildSuccess;
    private final Timer buildFailure;
    private final Timer pageAssembly;
    private final io.micrometer.core.instrument.Counter redisEvictions;
    private final Clock clock;
    private final long applicationStartedAtMillis;
    private final AtomicInteger buildsInFlight = new AtomicInteger();
    private final AtomicInteger waiters = new AtomicInteger();
    private final AtomicInteger consecutiveBuildFailures = new AtomicInteger();
    private final AtomicInteger redisCircuitOpen = new AtomicInteger();
    private final AtomicLong latestGenerationEpochMillis;
    private final AtomicLong lastRedisEvictions = new AtomicLong(-1L);

    public DownPriceSnapshotMetrics(MeterRegistry meterRegistry) {
        this(meterRegistry, Clock.systemUTC());
    }

    DownPriceSnapshotMetrics(MeterRegistry meterRegistry, Clock clock) {
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        applicationStartedAtMillis = clock.millis();
        latestGenerationEpochMillis = new AtomicLong(NO_GENERATION);
        buildSuccess = timer("snapshot.build.duration", "result", "success");
        buildFailure = timer("snapshot.build.duration", "result", "failure");
        pageAssembly = Timer.builder("snapshot.page.assemble.duration")
                .register(meterRegistry);
        redisEvictions = meterRegistry.counter("snapshot.redis.evictions");
        Gauge.builder("snapshot.build.inflight", buildsInFlight, AtomicInteger::get)
                .register(meterRegistry);
        Gauge.builder("snapshot.build.waiters", waiters, AtomicInteger::get)
                .register(meterRegistry);
        Gauge.builder(
                        "snapshot.build.consecutive.failures",
                        consecutiveBuildFailures,
                        AtomicInteger::get
                )
                .register(meterRegistry);
        Gauge.builder("snapshot.redis.circuit", redisCircuitOpen, AtomicInteger::get)
                .register(meterRegistry);
        Gauge.builder(
                        "snapshot.generation.age",
                        latestGenerationEpochMillis,
                        value -> generationAgeSeconds(value.get())
                )
                .baseUnit("seconds")
                .register(meterRegistry);
    }

    public void recordLookup(String source, String result) {
        meterRegistry.counter(
                "snapshot.lookup",
                "source", source,
                "result", result
        ).increment();
    }

    public void buildStarted() {
        buildsInFlight.incrementAndGet();
    }

    public void buildFinished(Duration duration, boolean success) {
        buildsInFlight.decrementAndGet();
        if (success) {
            consecutiveBuildFailures.set(0);
            buildSuccess.record(duration);
            return;
        }
        consecutiveBuildFailures.incrementAndGet();
        buildFailure.record(duration);
    }

    public void waiterStarted() {
        waiters.incrementAndGet();
    }

    public void waiterFinished() {
        waiters.decrementAndGet();
    }

    public void recordPublish(boolean success) {
        meterRegistry.counter(
                "snapshot.publish",
                "result", success ? "success" : "failure"
        ).increment();
    }

    public void recordGenerationAge(Duration age) {
        long generationEpochMillis = clock.millis() - Math.max(0L, age.toMillis());
        latestGenerationEpochMillis.accumulateAndGet(generationEpochMillis, Math::max);
    }

    public void recordReset(String reason) {
        meterRegistry.counter("snapshot.reset", "reason", reason).increment();
    }

    public void setRedisCircuitOpen(boolean open) {
        redisCircuitOpen.set(open ? 1 : 0);
    }

    public void recordRedisEvictions(long serverTotal) {
        if (serverTotal < 0) {
            return;
        }
        long previous = lastRedisEvictions.getAndSet(serverTotal);
        long delta = previous < 0
                ? serverTotal
                : serverTotal >= previous ? serverTotal - previous : serverTotal;
        redisEvictions.increment(delta);
    }

    public Timer.Sample startPageAssembly() {
        return Timer.start(meterRegistry);
    }

    public void finishPageAssembly(Timer.Sample sample) {
        sample.stop(pageAssembly);
    }

    private Timer timer(String name, String tagName, String tagValue) {
        return Timer.builder(name)
                .tag(tagName, tagValue)
                .register(meterRegistry);
    }

    private double generationAgeSeconds(long generationEpochMillis) {
        long referenceEpochMillis = generationEpochMillis == NO_GENERATION
                ? applicationStartedAtMillis
                : generationEpochMillis;
        return Math.max(0L, clock.millis() - referenceEpochMillis) / 1_000D;
    }
}
