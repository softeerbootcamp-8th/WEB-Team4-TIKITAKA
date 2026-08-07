package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.global.config.PendingAuctionImageProperties;
import com.tikitaka.bidwinback.global.storage.ObjectDeletionResult;
import com.tikitaka.bidwinback.global.storage.ObjectStorage;
import com.tikitaka.bidwinback.upload.domain.PendingAuctionImageStore;
import com.tikitaka.bidwinback.upload.domain.entity.PendingAuctionImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingAuctionImageCleanupService {

    private final PendingAuctionImageStore pendingAuctionImageStore;
    private final ObjectStorage objectStorage;
    private final PendingAuctionImageProperties cleanupProperties;
    private final Clock clock;

    public int cleanup() {
        LocalDateTime cutoff = LocalDateTime.ofInstant(
                clock.instant().minus(cleanupProperties.retention()),
                ZoneId.systemDefault()
        );
        List<PendingAuctionImage> expiredImages =
                pendingAuctionImageStore.findExpiredBefore(
                        cutoff,
                        cleanupProperties.cleanupBatchSize()
                );

        if (expiredImages.isEmpty()) {
            return 0;
        }

        List<String> expiredKeys = expiredImages.stream()
                .map(PendingAuctionImage::getObjectKey)
                .toList();
        ObjectDeletionResult result = objectStorage.deleteAll(expiredKeys);

        result.failures().forEach(failure -> {
            log.warn(
                    "만료 경매 이미지 삭제 실패: objectKey={}, code={}",
                    failure.objectKey(),
                    failure.code()
            );
        });

        // S3 삭제에 실패한 항목은 DB에 남겨 다음 스케줄 실행에서 재시도한다.
        if (!result.deletedKeys().isEmpty()) {
            pendingAuctionImageStore.deleteByObjectKeyIn(result.deletedKeys());
        }

        return result.deletedKeys().size();
    }
}
