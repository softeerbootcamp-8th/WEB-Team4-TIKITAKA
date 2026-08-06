package com.tikitaka.bidwinback.global.sse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_INPUT_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class SseHubTest {

    private final List<SseHub> hubs = new ArrayList<>();

    @AfterEach
    void tearDown() {
        hubs.forEach(SseHub::closeConnections);
    }

    @Test
    void 메시지는_같은_채널을_구독한_연결에만_전송한다() throws Exception {
        // given
        SseHub hub = hub(3);
        SseChannel auction = channel("auction", "1");
        SseEmitter auctionSubscriber = mock(SseEmitter.class);
        SseEmitter notificationSubscriber = mock(SseEmitter.class);
        hub.subscribe(List.of(auction), auctionSubscriber, List::of);
        hub.subscribe(
                List.of(channel("member", "1")),
                notificationSubscriber,
                List::of
        );

        // when
        hub.publish(message(auction, "price-changed", 1L));

        // then
        verify(auctionSubscriber, timeout(1_000))
                .send(any(SseEmitter.SseEventBuilder.class));
        verify(notificationSubscriber, after(100).never())
                .send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void 같은_채널의_서로_다른_이벤트는_같은_version이어도_모두_전송한다() throws Exception {
        // given
        SseHub hub = hub(3);
        SseChannel channel = channel("auction", "1");
        SseEmitter emitter = mock(SseEmitter.class);
        hub.subscribe(List.of(channel), emitter, List::of);

        // when
        hub.publish(message(channel, "price-changed", 1L));
        hub.publish(message(channel, "auction-closed", 1L));

        // then
        verify(emitter, timeout(1_000).times(2))
                .send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void broadcast는_구독_채널과_관계없이_모든_연결에_전송한다() throws Exception {
        // given
        SseHub hub = hub(3);
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        hub.subscribe(List.of(channel("auction", "1")), first, List::of);
        hub.subscribe(List.of(channel("member", "2")), second, List::of);

        // when
        hub.broadcast(message(channel("system", "heartbeat"), "heartbeat", 1L));

        // then
        verify(first, timeout(1_000)).send(any(SseEmitter.SseEventBuilder.class));
        verify(second, timeout(1_000)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void 초기_snapshot_조회_중_변경되어도_더_높은_version을_유지한다() throws Exception {
        // given
        SseHub hub = hub(3);
        SseChannel channel = channel("auction", "1");
        SseEmitter emitter = mock(SseEmitter.class);
        ArgumentCaptor<SseEmitter.SseEventBuilder> event =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);

        // when
        hub.subscribe(List.of(channel), emitter, () -> {
            hub.publish(message(channel, "auction-state", 2L));
            return List.of(message(channel, "auction-state", 1L));
        });

        // then
        verify(emitter, after(100).times(1)).send(event.capture());
        assertThat(event.getValue().build())
                .extracting(ResponseBodyEmitter.DataWithMediaType::getData)
                .contains("auction-state-2");
    }

    @Test
    void 초기_snapshot이_구독하지_않은_채널이면_연결을_정리하고_거부한다() {
        // given
        SseHub hub = hub(3);
        SseChannel subscribed = channel("auction", "1");
        SseEmitter emitter = mock(SseEmitter.class);

        // when & then
        assertThatThrownBy(() -> hub.subscribe(
                List.of(subscribed),
                emitter,
                () -> List.of(message(channel("auction", "2"), "auction-state", 1L))
        )).isInstanceOf(IllegalArgumentException.class);
        assertThat(hub.connectionCount()).isZero();
        assertThat(hub.hasSubscribers(subscribed)).isFalse();
    }

    @Test
    void 중복된_채널은_구독_상한을_한_번만_차지한다() {
        // given
        SseHub hub = hub(2);
        SseChannel first = channel("auction", "1");
        SseChannel second = channel("member", "1");

        // when & then
        assertThatCode(() -> hub.subscribe(
                List.of(first, first, second),
                mock(SseEmitter.class),
                List::of
        )).doesNotThrowAnyException();
    }

    @Test
    void 한_연결의_채널_상한을_넘으면_거부한다() {
        // given
        SseHub hub = hub(2);

        // when & then
        assertThatThrownBy(() -> hub.subscribe(
                List.of(
                        channel("auction", "1"),
                        channel("auction", "2"),
                        channel("member", "1")
                ),
                mock(SseEmitter.class),
                List::of
        )).isInstanceOfSatisfying(
                SseException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(INVALID_INPUT_VALUE)
        );
    }

    @Test
    void 구독할_채널이_없으면_거부한다() {
        // given
        SseHub hub = hub(2);

        // when & then
        assertThatThrownBy(() -> hub.subscribe(
                List.of(),
                mock(SseEmitter.class),
                List::of
        )).isInstanceOf(SseException.class);
    }

    @Test
    void 완료된_연결은_모든_채널_색인에서_제거한다() {
        // given
        SseHub hub = hub(3);
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicReference<Runnable> completion = completionOf(emitter);
        SseChannel first = channel("auction", "1");
        SseChannel second = channel("member", "1");
        hub.subscribe(List.of(first, second), emitter, List::of);

        // when
        completion.get().run();

        // then
        assertThat(hub.connectionCount()).isZero();
        assertThat(hub.hasSubscribers(first)).isFalse();
        assertThat(hub.hasSubscribers(second)).isFalse();
    }

    @Test
    void 마지막_연결_해지와_새_구독이_겹쳐도_새_연결은_채널_이벤트를_받는다() throws Exception {
        // given
        SseHub hub = hub(1);
        SseChannel channel = channel("auction", "1");
        SseEmitter currentEmitter = mock(SseEmitter.class);
        AtomicReference<Runnable> currentCompletion = completionOf(currentEmitter);
        hub.subscribe(List.of(channel), currentEmitter, List::of);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (long version = 1; version <= 200; version++) {
                SseEmitter nextEmitter = mock(SseEmitter.class);
                AtomicReference<Runnable> nextCompletion = completionOf(nextEmitter);
                CountDownLatch start = new CountDownLatch(1);
                Runnable closing = currentCompletion.get();

                // when
                Future<?> unsubscribe = executor.submit(() -> {
                    await(start);
                    closing.run();
                });
                Future<?> subscribe = executor.submit(() -> {
                    await(start);
                    hub.subscribe(List.of(channel), nextEmitter, List::of);
                });
                start.countDown();
                unsubscribe.get();
                subscribe.get();
                hub.publish(message(channel, "auction-state", version));

                // then
                verify(nextEmitter, timeout(1_000))
                        .send(any(SseEmitter.SseEventBuilder.class));
                currentCompletion = nextCompletion;
            }
        }
    }

    @Test
    void 타임아웃된_연결은_제거한다() {
        // given
        SseHub hub = hub(3);
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicReference<Runnable> timeout = new AtomicReference<>();
        doAnswer(invocation -> {
            timeout.set(invocation.getArgument(0));
            return null;
        }).when(emitter).onTimeout(any(Runnable.class));
        hub.subscribe(List.of(channel("auction", "1")), emitter, List::of);

        // when
        timeout.get().run();

        // then
        assertThat(hub.connectionCount()).isZero();
    }

    @Test
    void 오류가_난_연결은_제거한다() {
        // given
        SseHub hub = hub(3);
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicReference<Consumer<Throwable>> error = new AtomicReference<>();
        doAnswer(invocation -> {
            error.set(invocation.getArgument(0));
            return null;
        }).when(emitter).onError(any());
        hub.subscribe(List.of(channel("auction", "1")), emitter, List::of);

        // when
        error.get().accept(new IllegalStateException("disconnected"));

        // then
        assertThat(hub.connectionCount()).isZero();
    }

    @Test
    void 종료하면_열린_연결을_모두_닫는다() {
        // given
        SseHub hub = hub(3);
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        hub.subscribe(List.of(channel("auction", "1")), first, List::of);
        hub.subscribe(List.of(channel("member", "1")), second, List::of);

        // when
        hub.closeConnections();

        // then
        verify(first).complete();
        verify(second).complete();
        assertThat(hub.connectionCount()).isZero();
    }

    private AtomicReference<Runnable> completionOf(SseEmitter emitter) {
        AtomicReference<Runnable> completion = new AtomicReference<>();
        doAnswer(invocation -> {
            completion.set(invocation.getArgument(0));
            return null;
        }).when(emitter).onCompletion(any(Runnable.class));
        return completion;
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private SseHub hub(int maxChannelsPerConnection) {
        SseHub hub = new SseHub(
                300_000L,
                3_000L,
                maxChannelsPerConnection,
                100
        );
        hubs.add(hub);
        return hub;
    }

    private SseChannel channel(String namespace, String key) {
        return new SseChannel(namespace, key);
    }

    private SseMessage<String> message(
            SseChannel channel,
            String eventName,
            long version
    ) {
        return new SseMessage<>(
                channel,
                eventName,
                version,
                eventName + "-" + version
        );
    }
}
