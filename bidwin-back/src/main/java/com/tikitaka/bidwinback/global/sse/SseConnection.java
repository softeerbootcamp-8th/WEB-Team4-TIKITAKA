package com.tikitaka.bidwinback.global.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** 한 클라이언트의 전송 순서와 대기 메시지를 관리한다. */
final class SseConnection {

    private static final ThreadFactory WRITER_THREADS =
            Thread.ofVirtual().name("sse-writer-", 0).factory();

    private final SseEmitter emitter;
    private final long reconnectTimeMs;
    private final Consumer<SseConnection> onClosed;
    private final BlockingQueue<PendingMessage> pendingMessages;
    private final Map<MessageKey, Long> latestVersions = new HashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread writerThread;
    private final SseMetrics metrics;

    SseConnection(
            SseEmitter emitter,
            long reconnectTimeMs,
            int maxPendingMessages,
            Consumer<SseConnection> onClosed,
            SseMetrics metrics
    ) {
        this.emitter = emitter;
        this.reconnectTimeMs = reconnectTimeMs;
        this.onClosed = onClosed;
        this.metrics = metrics;
        this.pendingMessages = new ArrayBlockingQueue<>(maxPendingMessages);
        this.writerThread = WRITER_THREADS.newThread(this::writeLoop);
    }

    void activate() {
        emitter.onCompletion(() -> terminate("completion"));
        emitter.onTimeout(() -> terminate("timeout"));
        emitter.onError(ignored -> terminate("error"));
        writerThread.start();
    }

    void send(SseMessage<?> message) {
        if (closed.get()) {
            return;
        }

        if (!pendingMessages.offer(new PendingMessage(message, System.nanoTime()))) {
            metrics.recordFailed(message, "queue_full");
            disconnectSlowConsumer();
            return;
        }
        // 종료와 offer가 교차해도 닫힌 연결에 메시지가 남지 않게 한다.
        if (closed.get()) {
            pendingMessages.clear();
        }
    }

    void close() {
        if (!terminate("shutdown")) {
            return;
        }
        complete();
    }

    private void disconnectSlowConsumer() {
        if (!terminate("slow_consumer")) {
            return;
        }
        // send가 Spring의 write lock을 쥐고 있어도 발행 스레드는 기다리지 않는다.
        Thread.startVirtualThread(this::complete);
    }

    private void complete() {
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // 연결 색인과 대기 데이터는 terminate에서 이미 정리했다.
        }
    }

    /** 단일 writer만 호출하므로 별도의 동기화 없이 전송 순서대로 버전을 판별한다. */
    private boolean acceptLatestVersion(SseMessage<?> message) {
        MessageKey key = MessageKey.from(message);
        Long current = latestVersions.get(key);
        if (current != null && message.version() <= current) {
            return false;
        }
        latestVersions.put(key, message.version());
        return true;
    }

    int pendingMessageCount() {
        return pendingMessages.size();
    }

    private void writeLoop() {
        try {
            while (!closed.get()) {
                PendingMessage pendingMessage = pendingMessages.take();
                SseMessage<?> message = pendingMessage.message();
                if (!acceptLatestVersion(message)) {
                    metrics.recordSuppressed(message);
                    continue;
                }
                long writeStartedAtNanos = System.nanoTime();
                try {
                    emitter.send(SseEmitter.event()
                            .name(message.eventName())
                            .reconnectTime(reconnectTimeMs)
                            .data(message.data()));
                    metrics.recordSent(
                            message,
                            pendingMessage.enqueuedAtNanos(),
                            writeStartedAtNanos
                    );
                } catch (IOException exception) {
                    metrics.recordFailed(message, "io");
                    terminate("io_error");
                } catch (RuntimeException exception) {
                    metrics.recordFailed(message, "application");
                    completeWithError(exception);
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            pendingMessages.clear();
            latestVersions.clear();
        }
    }

    private void completeWithError(RuntimeException exception) {
        if (!terminate("application_error")) {
            return;
        }
        try {
            // 직렬화 등 애플리케이션 오류는 컨테이너 I/O 콜백을 기대할 수 없다.
            emitter.completeWithError(exception);
        } catch (RuntimeException ignored) {
            // 색인과 writer는 terminate에서 이미 정리됐다.
        }
    }

    private boolean terminate(String reason) {
        if (!closed.compareAndSet(false, true)) {
            return false;
        }
        writerThread.interrupt();
        pendingMessages.clear();
        metrics.recordClosed(reason);
        onClosed.accept(this);
        return true;
    }

    private record MessageKey(SseChannel channel, String eventName) {

        private static MessageKey from(SseMessage<?> message) {
            return new MessageKey(message.channel(), message.eventName());
        }
    }

    private record PendingMessage(SseMessage<?> message, long enqueuedAtNanos) {
    }
}
