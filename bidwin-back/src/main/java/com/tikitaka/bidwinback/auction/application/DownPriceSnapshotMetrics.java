package com.tikitaka.bidwinback.auction.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class DownPriceSnapshotMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer buildSuccess;
    private final Timer buildFailure;
    private final Timer pageAssembly;
    private final AtomicInteger buildsInFlight = new AtomicInteger();
    private final AtomicInteger waiters = new AtomicInteger();
    private final AtomicInteger consecutiveBuildFailures = new AtomicInteger();
    private final AtomicInteger redisCircuitOpen = new AtomicInteger();
    private final AtomicLong generationAgeMillis = new AtomicLong();

    public DownPriceSnapshotMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        buildSuccess = timer("snapshot.build.duration", "result", "success");
        buildFailure = timer("snapshot.build.duration", "result", "failure");
        pageAssembly = Timer.builder("snapshot.page.assemble.duration")
                .register(meterRegistry);
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
                        generationAgeMillis,
                        value -> value.doubleValue() / 1_000D
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
        generationAgeMillis.set(Math.max(0L, age.toMillis()));
    }

    public void recordReset(String reason) {
        meterRegistry.counter("snapshot.reset", "reason", reason).increment();
    }

    public void setRedisCircuitOpen(boolean open) {
        redisCircuitOpen.set(open ? 1 : 0);
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
}
