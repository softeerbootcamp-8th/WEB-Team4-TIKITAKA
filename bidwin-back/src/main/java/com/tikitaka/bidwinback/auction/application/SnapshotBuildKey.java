package com.tikitaka.bidwinback.auction.application;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record SnapshotBuildKey(LocalDateTime generationAt) {

    private static final int GENERATION_SECONDS = 30;

    public SnapshotBuildKey {
        Objects.requireNonNull(generationAt, "세대 시각은 필수입니다.");
        generationAt = generationAt.truncatedTo(ChronoUnit.MILLIS);
    }

    public static SnapshotBuildKey latestSlot(LocalDateTime databaseTime) {
        LocalDateTime seconds = databaseTime.truncatedTo(ChronoUnit.SECONDS);
        int slotSecond = seconds.getSecond() / GENERATION_SECONDS * GENERATION_SECONDS;
        return new SnapshotBuildKey(seconds.withSecond(slotSecond));
    }

    public static SnapshotBuildKey exact(LocalDateTime generationAt) {
        return new SnapshotBuildKey(generationAt);
    }
}
