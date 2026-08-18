package com.tikitaka.bidwinback.auction.application;

import java.time.LocalDateTime;

public record ResolvedDownPriceSnapshotPage(
        DownPriceSnapshotPage page,
        LocalDateTime serverTime,
        int effectivePage,
        SnapshotResetReason resetReason
) {

    public boolean reset() {
        return resetReason != null;
    }
}
