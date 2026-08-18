package com.tikitaka.bidwinback.auction.application;

import java.time.LocalDateTime;

public record ResolvedSnapshot(
        SnapshotGenerationPage snapshot,
        LocalDateTime serverTime,
        int effectivePage,
        boolean reset,
        SnapshotResetReason resetReason
) {
}
