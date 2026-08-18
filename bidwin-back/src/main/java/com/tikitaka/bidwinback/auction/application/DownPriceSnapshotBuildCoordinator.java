package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.infrastructure.RedisSnapshotStore;
import com.tikitaka.bidwinback.auction.infrastructure.RedisSnapshotUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Slf4j
@Component
public class DownPriceSnapshotBuildCoordinator {

    private final ConcurrentHashMap<SnapshotBuildKey, CompletableFuture<DownPriceSnapshot>>
            inFlight = new ConcurrentHashMap<>();

    private final SnapshotCaptureService captureService;
    private final RedisSnapshotStore redisStore;
    private final DownPriceSnapshotMetrics metrics;
    private final Executor snapshotBuildExecutor;

    public DownPriceSnapshotBuildCoordinator(
            SnapshotCaptureService captureService,
            RedisSnapshotStore redisStore,
            DownPriceSnapshotMetrics metrics,
            @Qualifier("snapshotBuildExecutor") Executor snapshotBuildExecutor
    ) {
        this.captureService = captureService;
        this.redisStore = redisStore;
        this.metrics = metrics;
        this.snapshotBuildExecutor = snapshotBuildExecutor;
    }

    public CompletableFuture<DownPriceSnapshot> getOrBuild(SnapshotBuildKey key) {
        CompletableFuture<DownPriceSnapshot> created = new CompletableFuture<>();
        CompletableFuture<DownPriceSnapshot> existing = inFlight.putIfAbsent(key, created);
        if (existing != null) {
            metrics.waiterStarted();
            existing.whenComplete((ignored, exception) -> metrics.waiterFinished());
            return existing;
        }

        metrics.buildStarted();
        long startedAt = System.nanoTime();
        try {
            snapshotBuildExecutor.execute(() -> build(key, created, startedAt));
        } catch (RuntimeException exception) {
            inFlight.remove(key, created);
            metrics.buildFinished(Duration.ofNanos(System.nanoTime() - startedAt), false);
            created.completeExceptionally(exception);
        }
        return created;
    }

    private void build(
            SnapshotBuildKey key,
            CompletableFuture<DownPriceSnapshot> future,
            long startedAt
    ) {
        boolean success = false;
        try {
            DownPriceSnapshot snapshot = captureService.capture(key);
            try {
                redisStore.publish(snapshot);
            } catch (RedisSnapshotUnavailableException exception) {
                log.warn(
                        "Redis에 하향 가격 스냅샷을 발행하지 못해 DB 캡처 결과로 응답합니다. generationAt={}",
                        snapshot.generationAt(),
                        exception
                );
            }
            success = true;
            future.complete(snapshot);
        } catch (Throwable exception) {
            future.completeExceptionally(exception);
        } finally {
            inFlight.remove(key, future);
            metrics.buildFinished(Duration.ofNanos(System.nanoTime() - startedAt), success);
        }
    }
}
