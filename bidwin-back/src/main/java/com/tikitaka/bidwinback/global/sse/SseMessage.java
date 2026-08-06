package com.tikitaka.bidwinback.global.sse;

import java.util.Objects;

public record SseMessage<T>(
        SseChannel channel,
        String eventName,
        long version,
        T data
) {

    public SseMessage {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(data, "data");
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException("SSE 이벤트 이름은 비어 있을 수 없습니다.");
        }
        if (version < 0) {
            throw new IllegalArgumentException("SSE 이벤트 버전은 음수일 수 없습니다.");
        }
    }
}
