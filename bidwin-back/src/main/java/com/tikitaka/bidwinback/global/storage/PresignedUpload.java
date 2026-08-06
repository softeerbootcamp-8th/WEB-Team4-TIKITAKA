package com.tikitaka.bidwinback.global.storage;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PresignedUpload(
        String url,
        Map<String, List<String>> signedHeaders,
        Instant expiresAt
) {
    public PresignedUpload {
        signedHeaders = Map.copyOf(signedHeaders);
    }
}
