package com.tikitaka.bidwinback.global.storage;

public record StoredObjectMetadata(
        long contentLength,
        String contentType,
        String checksumSha256
) {
}
