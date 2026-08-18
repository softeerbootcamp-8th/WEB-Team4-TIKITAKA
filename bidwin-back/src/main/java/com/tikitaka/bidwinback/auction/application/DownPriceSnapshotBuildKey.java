package com.tikitaka.bidwinback.auction.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record DownPriceSnapshotBuildKey(LocalDateTime generationAt) {

    static final Duration GENERATION_INTERVAL = Duration.ofSeconds(30);
    private static final int GENERATION_SECONDS = Math.toIntExact(
            GENERATION_INTERVAL.toSeconds()
    );

    public DownPriceSnapshotBuildKey {
        Objects.requireNonNull(generationAt, "세대 시각은 필수입니다.");
        generationAt = generationAt.truncatedTo(ChronoUnit.MILLIS);
    }

    public static DownPriceSnapshotBuildKey latestSlot(LocalDateTime databaseTime) {
        LocalDateTime seconds = databaseTime.truncatedTo(ChronoUnit.SECONDS);
        int slotSecond = seconds.getSecond() / GENERATION_SECONDS * GENERATION_SECONDS;
        return new DownPriceSnapshotBuildKey(seconds.withSecond(slotSecond));
    }

    public static DownPriceSnapshotBuildKey exact(LocalDateTime generationAt) {
        return new DownPriceSnapshotBuildKey(generationAt);
    }

    public static boolean isGenerationSlot(LocalDateTime generationAt) {
        return generationAt.equals(latestSlot(generationAt).generationAt());
    }
}
