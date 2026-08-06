package com.tikitaka.bidwinback.global.sse;

public record SseChannel(String namespace, String key) {

    public SseChannel {
        if (namespace == null || namespace.isBlank()
                || key == null || key.isBlank()) {
            throw new IllegalArgumentException("SSE 채널 namespace와 key는 비어 있을 수 없습니다.");
        }
    }
}
