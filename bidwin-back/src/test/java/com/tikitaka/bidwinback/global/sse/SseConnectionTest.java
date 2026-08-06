package com.tikitaka.bidwinback.global.sse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class SseConnectionTest {

    private static final SseChannel AUCTION_CHANNEL =
            new SseChannel("auction", "1");
    private final List<SseConnection> connections = new ArrayList<>();

    @AfterEach
    void tearDown() {
        connections.forEach(SseConnection::close);
    }

    @Test
    void 전송에_실패하면_해당_연결을_제거한다() throws Exception {
        // given
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("disconnected"))
                .when(emitter)
                .send(any(SseEmitter.SseEventBuilder.class));
        CountDownLatch removed = new CountDownLatch(1);
        SseConnection connection = connection(
                emitter,
                ignored -> removed.countDown()
        );

        // when
        connection.send(message(AUCTION_CHANNEL, "price-changed", 1L));

        // then
        assertThat(removed.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void 전송_실패는_emitter를_다시_완료하지_않는다() throws Exception {
        // given
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("disconnected"))
                .when(emitter)
                .send(any(SseEmitter.SseEventBuilder.class));
        SseConnection connection = connection(emitter);

        // when
        connection.send(message(AUCTION_CHANNEL, "price-changed", 1L));

        // then
        verify(emitter, timeout(1_000))
                .send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter, after(100).never()).complete();
        verify(emitter, after(100).never()).completeWithError(any());
    }

    @Test
    void 애플리케이션_전송_오류는_emitter를_오류로_완료한다() throws Exception {
        // given
        SseEmitter emitter = mock(SseEmitter.class);
        IllegalStateException failure = new IllegalStateException("serialization failed");
        doThrow(failure)
                .when(emitter)
                .send(any(SseEmitter.SseEventBuilder.class));
        SseConnection connection = connection(emitter);

        // when
        connection.send(message(AUCTION_CHANNEL, "price-changed", 1L));

        // then
        verify(emitter, timeout(1_000)).completeWithError(failure);
    }

    @Test
    void socket_write는_발행_호출자가_아닌_전용_가상_스레드가_수행한다() throws Exception {
        // given
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicReference<Thread> writer = new AtomicReference<>();
        doAnswer(invocation -> {
            writer.set(Thread.currentThread());
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        Thread publisher = Thread.currentThread();
        SseConnection connection = connection(emitter);

        // when
        connection.send(message(AUCTION_CHANNEL, "price-changed", 1L));

        // then
        verify(emitter, timeout(1_000))
                .send(any(SseEmitter.SseEventBuilder.class));
        assertThat(writer.get().isVirtual()).isTrue();
        assertThat(writer.get()).isNotSameAs(publisher);
    }

    @Test
    void 전송_중에_들어온_메시지도_같은_writer가_순서대로_전송한다() throws Exception {
        // given
        SseEmitter emitter = mock(SseEmitter.class);
        CountDownLatch firstWriteStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstWrite = new CountDownLatch(1);
        AtomicInteger writes = new AtomicInteger();
        doAnswer(invocation -> {
            if (writes.incrementAndGet() == 1) {
                firstWriteStarted.countDown();
                releaseFirstWrite.await();
            }
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        SseConnection connection = connection(emitter);
        connection.send(message(AUCTION_CHANNEL, "price-changed", 1L));
        assertThat(firstWriteStarted.await(1, TimeUnit.SECONDS)).isTrue();

        // when
        connection.send(message(AUCTION_CHANNEL, "price-changed", 2L));
        releaseFirstWrite.countDown();

        // then
        verify(emitter, timeout(1_000).times(2))
                .send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void 대기열이_가득_차면_느린_연결을_발행_스레드_밖에서_종료한다() throws Exception {
        // given
        SseEmitter emitter = mock(SseEmitter.class);
        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch releaseWrite = new CountDownLatch(1);
        CountDownLatch removed = new CountDownLatch(1);
        AtomicReference<Thread> completionThread = new AtomicReference<>();
        doAnswer(invocation -> {
            writeStarted.countDown();
            boolean released = false;
            while (!released) {
                try {
                    released = releaseWrite.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    // 큐 초과로 writer가 중단돼도 실제 socket write가 끝날 때까지 기다리는 상황을 재현한다.
                }
            }
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        doAnswer(invocation -> {
            completionThread.set(Thread.currentThread());
            return null;
        }).when(emitter).complete();
        SseConnection connection = connection(
                emitter,
                1,
                ignored -> removed.countDown()
        );
        connection.send(message(AUCTION_CHANNEL, "price-changed", 1L));
        assertThat(writeStarted.await(1, TimeUnit.SECONDS)).isTrue();
        connection.send(message(AUCTION_CHANNEL, "price-changed", 2L));

        try {
            // when
            Thread publisher = Thread.currentThread();
            connection.send(message(AUCTION_CHANNEL, "price-changed", 3L));

            // then
            assertThat(removed.await(1, TimeUnit.SECONDS)).isTrue();
            verify(emitter, timeout(1_000)).complete();
            assertThat(completionThread.get().isVirtual()).isTrue();
            assertThat(completionThread.get()).isNotSameAs(publisher);
        } finally {
            releaseWrite.countDown();
        }
    }

    @Test
    void 같은_채널과_이벤트의_중복_version은_전송하지_않는다() throws Exception {
        // given
        SseEmitter emitter = mock(SseEmitter.class);
        SseConnection connection = connection(emitter);
        connection.send(message(AUCTION_CHANNEL, "price-changed", 2L));
        verify(emitter, timeout(1_000))
                .send(any(SseEmitter.SseEventBuilder.class));
        clearInvocations(emitter);

        // when
        connection.send(message(AUCTION_CHANNEL, "price-changed", 2L));

        // then
        verify(emitter, after(100).never())
                .send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void 같은_채널과_이벤트의_낮은_version은_전송하지_않는다() throws Exception {
        // given
        SseEmitter emitter = mock(SseEmitter.class);
        SseConnection connection = connection(emitter);
        connection.send(message(AUCTION_CHANNEL, "price-changed", 7L));
        verify(emitter, timeout(1_000))
                .send(any(SseEmitter.SseEventBuilder.class));
        clearInvocations(emitter);

        // when
        connection.send(message(AUCTION_CHANNEL, "price-changed", 5L));

        // then
        verify(emitter, after(100).never())
                .send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void 서로_다른_이벤트는_같은_version이어도_각각_전송한다() throws Exception {
        // given
        SseEmitter emitter = mock(SseEmitter.class);
        SseConnection connection = connection(emitter);

        // when
        connection.send(message(AUCTION_CHANNEL, "price-changed", 1L));
        connection.send(message(AUCTION_CHANNEL, "auction-closed", 1L));

        // then
        verify(emitter, timeout(1_000).times(2))
                .send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void 서로_다른_채널은_같은_이벤트와_version이어도_각각_전송한다() throws Exception {
        // given
        SseEmitter emitter = mock(SseEmitter.class);
        SseConnection connection = connection(emitter);

        // when
        connection.send(message(AUCTION_CHANNEL, "state", 1L));
        connection.send(message(new SseChannel("member", "1"), "state", 1L));

        // then
        verify(emitter, timeout(1_000).times(2))
                .send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void 메시지는_event_이름과_payload로_전송한다() throws Exception {
        // given
        SseEmitter emitter = mock(SseEmitter.class);
        ArgumentCaptor<SseEmitter.SseEventBuilder> event =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        SseConnection connection = connection(emitter);

        // when
        connection.send(message(AUCTION_CHANNEL, "price-changed", 3L));

        // then
        verify(emitter, timeout(1_000)).send(event.capture());
        assertThat(event.getValue().build())
                .extracting(ResponseBodyEmitter.DataWithMediaType::getData)
                .contains("price-changed-3");
    }

    private SseConnection connection(SseEmitter emitter) {
        return connection(emitter, ignored -> { });
    }

    private SseConnection connection(
            SseEmitter emitter,
            java.util.function.Consumer<SseConnection> onClosed
    ) {
        return connection(emitter, 100, onClosed);
    }

    private SseConnection connection(
            SseEmitter emitter,
            int maxPendingMessages,
            java.util.function.Consumer<SseConnection> onClosed
    ) {
        SseConnection connection = new SseConnection(
                emitter,
                3_000L,
                maxPendingMessages,
                onClosed
        );
        connection.activate();
        connections.add(connection);
        return connection;
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
