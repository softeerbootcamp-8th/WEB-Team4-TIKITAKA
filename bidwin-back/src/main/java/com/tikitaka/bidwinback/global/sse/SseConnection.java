package com.tikitaka.bidwinback.global.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(SseConnection.class);
    private static final ThreadFactory WRITER_THREADS =
            Thread.ofVirtual().name("sse-writer-", 0).factory();

    private final SseEmitter emitter;
    private final long reconnectTimeMs;
    private final Consumer<SseConnection> onClosed;
    private final BlockingQueue<SseMessage<?>> pendingMessages;
    private final Map<MessageKey, Long> latestVersions = new HashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread writerThread;

    SseConnection(
            SseEmitter emitter,
            long reconnectTimeMs,
            int maxPendingMessages,
            Consumer<SseConnection> onClosed
    ) {
        this.emitter = emitter;
        this.reconnectTimeMs = reconnectTimeMs;
        this.onClosed = onClosed;
        this.pendingMessages = new ArrayBlockingQueue<>(maxPendingMessages);
        this.writerThread = WRITER_THREADS.newThread(this::writeLoop);
    }

    void activate() {
        emitter.onCompletion(this::terminate);
        emitter.onTimeout(this::terminate);
        emitter.onError(ignored -> terminate());
        writerThread.start();
    }

    void send(SseMessage<?> message) {
        if (closed.get()) {
            return;
        }

        if (!pendingMessages.offer(message)) {
            disconnectSlowConsumer();
            return;
        }
        // 종료와 offer가 교차해도 닫힌 연결에 메시지가 남지 않게 한다.
        if (closed.get()) {
            pendingMessages.clear();
        }
    }

    void close() {
        if (!terminate()) {
            return;
        }
        complete();
    }

    private void disconnectSlowConsumer() {
        if (!terminate()) {
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

    private void writeLoop() {
        SseMessage<?> message = null;
        try {
            while (!closed.get()) {
                message = pendingMessages.take();
                if (!acceptLatestVersion(message)) {
                    continue;
                }
                emitter.send(SseEmitter.event()
                        .name(message.eventName())
                        .reconnectTime(reconnectTimeMs)
                        .data(message.data()));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            // I/O 실패는 컨테이너가 오류 완료하므로 색인만 정리한다.
            terminate();
        } catch (RuntimeException exception) {
            log.atWarn()
                    .setCause(exception)
                    .addKeyValue("event", "sse_message_send_failed")
                    .addKeyValue("channelNamespace", message == null ? null : message.channel().namespace())
                    .addKeyValue("channelKey", message == null ? null : message.channel().key())
                    .addKeyValue("eventName", message == null ? null : message.eventName())
                    .log("SSE message send failed");
            // completeWithError는 MVC가 일반 오류 본문을 SSE 스트림에 쓰게 하므로
            // 이미 열린 스트림만 종료해 클라이언트가 재연결하도록 한다.
            close();
        } finally {
            pendingMessages.clear();
            latestVersions.clear();
        }
    }

    private boolean terminate() {
        if (!closed.compareAndSet(false, true)) {
            return false;
        }
        writerThread.interrupt();
        pendingMessages.clear();
        onClosed.accept(this);
        return true;
    }

    private record MessageKey(SseChannel channel, String eventName) {

        private static MessageKey from(SseMessage<?> message) {
            return new MessageKey(message.channel(), message.eventName());
        }
    }
}
