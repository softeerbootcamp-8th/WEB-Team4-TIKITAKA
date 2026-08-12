package com.tikitaka.bidwinback.global.sse;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class SseMetricsTest {

    private static final SseChannel AUCTION_CHANNEL =
            new SseChannel("auction", "1");
    private final List<SseHub> hubs = new ArrayList<>();

    @AfterEach
    void tearDown() {
        hubs.forEach(SseHub::closeConnections);
    }

    @Test
    void 연결의_시작과_종료를_활성_연결_수와_종료_사유로_노출한다() {
        // given
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SseHub hub = hub(registry, 100, 1_000);
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicReference<Runnable> completion = completionOf(emitter);

        // when
        hub.subscribe(List.of(AUCTION_CHANNEL), emitter, List::of);

        // then
        assertThat(registry.get("bidwin.sse.connections.active").gauge().value())
                .isEqualTo(1);

        // when
        completion.get().run();

        // then
        assertThat(registry.get("bidwin.sse.connections.active").gauge().value())
                .isZero();
        assertThat(registry.get("bidwin.sse.connections.closed")
                .tag("reason", "completion")
                .counter()
                .count()).isEqualTo(1);
    }

    @Test
    void 발행과_실제_write를_구분하고_서버_전송_단계별_지연을_노출한다()
            throws Exception {
        // given
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SseHub hub = hub(registry, 100, 1_000);
        SseEmitter emitter = mock(SseEmitter.class);
        hub.subscribe(List.of(AUCTION_CHANNEL), emitter, List::of);

        // when
        hub.publish(message(1L), System.nanoTime());

        // then
        verify(emitter, timeout(1_000)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(eventCount(registry, "published", "none")).isEqualTo(1);
        assertThat(awaitValue(() -> eventCount(registry, "sent", "none")))
                .isEqualTo(1);
        assertThat(awaitValue(() -> timerCount(registry, "bidwin.sse.write")))
                .isEqualTo(1);
        assertThat(timerCount(registry, "bidwin.sse.delivery")).isEqualTo(1);
        assertThat(timerCount(registry, "bidwin.sse.queue")).isEqualTo(1);
        assertThat(timerCount(registry, "bidwin.sse.preparation")).isEqualTo(1);
    }

    @Test
    void writer가_막히면_연결별_대기열과_최댓값을_노출한다() throws Exception {
        // given
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SseHub hub = hub(registry, 2, 1_000);
        SseEmitter emitter = mock(SseEmitter.class);
        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch releaseWrite = new CountDownLatch(1);
        doAnswer(invocation -> {
            writeStarted.countDown();
            releaseWrite.await();
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        hub.subscribe(List.of(AUCTION_CHANNEL), emitter, List::of);
        hub.publish(message(1L));
        assertThat(writeStarted.await(1, TimeUnit.SECONDS)).isTrue();

        try {
            // when
            hub.publish(message(2L));

            // then
            assertThat(registry.get("bidwin.sse.messages.pending").gauge().value())
                    .isEqualTo(1);
            assertThat(registry.get("bidwin.sse.messages.pending.max").gauge().value())
                    .isEqualTo(1);
        } finally {
            releaseWrite.countDown();
        }
    }

    @Test
    void 대기열이_가득_차면_실패와_느린_연결_종료를_노출한다() throws Exception {
        // given
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SseHub hub = hub(registry, 1, 1_000);
        SseEmitter emitter = mock(SseEmitter.class);
        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch releaseWrite = new CountDownLatch(1);
        doAnswer(invocation -> {
            writeStarted.countDown();
            boolean released = false;
            while (!released) {
                try {
                    released = releaseWrite.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    // 느린 socket write가 writer interrupt만으로 끝나지 않는 상황을 재현한다.
                }
            }
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        hub.subscribe(List.of(AUCTION_CHANNEL), emitter, List::of);
        hub.publish(message(1L));
        assertThat(writeStarted.await(1, TimeUnit.SECONDS)).isTrue();
        hub.publish(message(2L));

        try {
            // when
            hub.publish(message(3L));

            // then
            assertThat(eventCount(registry, "failed", "queue_full")).isEqualTo(1);
            assertThat(registry.get("bidwin.sse.connections.closed")
                    .tag("reason", "slow_consumer")
                    .counter()
                    .count()).isEqualTo(1);
        } finally {
            releaseWrite.countDown();
        }
    }

    @Test
    void 같은_이벤트의_낮은_version을_억제한_횟수를_노출한다() throws Exception {
        // given
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SseHub hub = hub(registry, 100, 1_000);
        SseEmitter emitter = mock(SseEmitter.class);
        hub.subscribe(List.of(AUCTION_CHANNEL), emitter, List::of);
        hub.publish(message(2L));
        verify(emitter, timeout(1_000)).send(any(SseEmitter.SseEventBuilder.class));

        // when
        hub.publish(message(1L));

        // then
        assertThat(awaitValue(() -> eventCount(registry, "suppressed", "stale_version")))
                .isEqualTo(1);
    }

    private SseHub hub(
            SimpleMeterRegistry registry,
            int maxPendingMessages,
            int maxConnections
    ) {
        SseHub hub = new SseHub(
                300_000L,
                3_000L,
                50,
                maxPendingMessages,
                maxConnections,
                new SseMetrics(registry)
        );
        hubs.add(hub);
        return hub;
    }

    private AtomicReference<Runnable> completionOf(SseEmitter emitter) {
        AtomicReference<Runnable> completion = new AtomicReference<>();
        doAnswer(invocation -> {
            completion.set(invocation.getArgument(0));
            return null;
        }).when(emitter).onCompletion(any(Runnable.class));
        return completion;
    }

    private double eventCount(MeterRegistry registry, String result, String reason) {
        Counter counter = registry.find("bidwin.sse.events")
                .tags(
                        "namespace", "auction",
                        "event", "price-changed",
                        "result", result,
                        "reason", reason
                )
                .counter();
        return counter == null ? 0 : counter.count();
    }

    private double timerCount(MeterRegistry registry, String name) {
        Timer timer = registry.find(name).timer();
        return timer == null ? 0 : timer.count();
    }

    private double awaitValue(DoubleSupplier supplier) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        double value;
        do {
            value = supplier.getAsDouble();
            if (value > 0) {
                return value;
            }
            Thread.onSpinWait();
        } while (System.nanoTime() < deadline);
        return value;
    }

    private SseMessage<String> message(long version) {
        return new SseMessage<>(
                AUCTION_CHANNEL,
                "price-changed",
                version,
                "price-changed-" + version
        );
    }
}
