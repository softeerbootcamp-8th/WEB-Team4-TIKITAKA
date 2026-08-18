package com.tikitaka.bidwinback.auction.infrastructure;

public class RedisSnapshotUnavailableException extends RuntimeException {

    public RedisSnapshotUnavailableException(String message) {
        super(message);
    }

    public RedisSnapshotUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
