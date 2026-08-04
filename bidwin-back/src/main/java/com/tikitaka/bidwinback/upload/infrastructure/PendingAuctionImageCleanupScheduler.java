package com.tikitaka.bidwinback.upload.infrastructure;

import com.tikitaka.bidwinback.upload.application.PendingAuctionImageCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingAuctionImageCleanupScheduler {

    private final PendingAuctionImageCleanupService cleanupService;

    @Scheduled(
            fixedDelayString = "${app.storage.s3.pending-auction-image.cleanup-interval:1h}",
            initialDelayString = "${app.storage.s3.pending-auction-image.cleanup-interval:1h}"
    )
    public void cleanup() {
        int deletedCount = cleanupService.cleanup();
        if (deletedCount > 0) {
            log.info("만료 경매 이미지 정리 완료: count={}", deletedCount);
        }
    }
}
