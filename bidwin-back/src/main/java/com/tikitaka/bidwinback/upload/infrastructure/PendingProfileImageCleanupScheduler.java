package com.tikitaka.bidwinback.upload.infrastructure;

import com.tikitaka.bidwinback.upload.application.PendingProfileImageCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingProfileImageCleanupScheduler {

    private final PendingProfileImageCleanupService cleanupService;

    @Scheduled(
            fixedDelayString = "${app.storage.s3.pending-profile-image.cleanup-interval:1h}",
            initialDelayString = "${app.storage.s3.pending-profile-image.cleanup-interval:1h}"
    )
    public void cleanup() {
        int deletedCount = cleanupService.cleanup();
        if (deletedCount > 0) {
            log.atInfo()
                    .addKeyValue("event", "pending_profile_image_cleanup_completed")
                    .addKeyValue("deletedCount", deletedCount)
                    .log("미사용 프로필 이미지 정리 완료");
        }
    }
}
