package com.tikitaka.bidwinback.global.storage;

import java.util.List;

public record ObjectDeletionResult(
        List<String> deletedKeys,
        List<Failure> failures
) {
    public ObjectDeletionResult {
        deletedKeys = List.copyOf(deletedKeys);
        failures = List.copyOf(failures);
    }

    public record Failure(String objectKey, String code) {
    }
}
