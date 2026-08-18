package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.infrastructure.RedisSnapshotStore;
import com.tikitaka.bidwinback.auction.infrastructure.RedisSnapshotUnavailableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DownPriceSnapshotBuildCoordinatorTest {

    @Mock
    private DownPriceSnapshotCaptureService captureService;

    @Mock
    private RedisSnapshotStore redisStore;

    @Test
    void 같은_세대의_동시_miss는_같은_Future와_한번의_캡처를_공유한다() {
        AtomicReference<Runnable> buildTask = new AtomicReference<>();
        Executor controlledExecutor = buildTask::set;
        DownPriceSnapshotBuildCoordinator coordinator = coordinator(controlledExecutor);
        DownPriceSnapshotBuildKey key = DownPriceSnapshotBuildKey.exact(
                LocalDateTime.of(2026, 8, 18, 12, 0)
        );
        DownPriceSnapshot snapshot = new DownPriceSnapshot(
                key.generationAt(),
                List.of(),
                List.of()
        );
        when(captureService.capture(key)).thenReturn(snapshot);

        CompletableFuture<DownPriceSnapshot> first = coordinator.getOrBuild(key);
        CompletableFuture<DownPriceSnapshot> second = coordinator.getOrBuild(key);
        buildTask.get().run();

        assertThat(second).isSameAs(first);
        assertThat(first).isCompletedWithValue(snapshot);
        verify(captureService).capture(key);
        verify(redisStore).publish(snapshot);
    }

    @Test
    void Redis_발행이_실패해도_DB_캡처_결과로_완료한다() {
        Executor directExecutor = Runnable::run;
        DownPriceSnapshotBuildCoordinator coordinator = coordinator(directExecutor);
        DownPriceSnapshotBuildKey key = DownPriceSnapshotBuildKey.exact(
                LocalDateTime.of(2026, 8, 18, 12, 0)
        );
        DownPriceSnapshot snapshot = new DownPriceSnapshot(
                key.generationAt(),
                List.of(),
                List.of()
        );
        when(captureService.capture(key)).thenReturn(snapshot);
        doThrow(new RedisSnapshotUnavailableException("Redis 장애"))
                .when(redisStore).publish(snapshot);

        CompletableFuture<DownPriceSnapshot> result = coordinator.getOrBuild(key);

        assertThat(result).isCompletedWithValue(snapshot);
    }

    private DownPriceSnapshotBuildCoordinator coordinator(Executor executor) {
        return new DownPriceSnapshotBuildCoordinator(
                captureService,
                redisStore,
                new DownPriceSnapshotMetrics(new SimpleMeterRegistry()),
                executor
        );
    }
}
