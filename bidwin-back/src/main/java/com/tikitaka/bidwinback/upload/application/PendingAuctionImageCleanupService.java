package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.global.config.PendingAuctionImageProperties;
import com.tikitaka.bidwinback.upload.domain.entity.PendingAuctionImage;
import com.tikitaka.bidwinback.upload.domain.repository.PendingAuctionImageStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PendingAuctionImageCleanupService {

    private final PendingAuctionImageStore pendingAuctionImageStore;
    private final PendingAuctionImageProperties cleanupProperties;
    private final Clock clock;

    @Transactional
    public int cleanup() {
        LocalDateTime cutoff = LocalDateTime.ofInstant(
                clock.instant().minus(cleanupProperties.retention()),
                ZoneId.systemDefault()
        );
        // 경매 등록과 같은 예약 행을 잠가, 소비 중인 예약을 정리 작업이 삭제하지 않게 한다.
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
        // 방치된 temp/ 객체는 외부에서 설정한 S3 Lifecycle이 만료시키므로 예약 행만 정리한다.
        pendingAuctionImageStore.deleteByObjectKeyIn(expiredKeys);

        return expiredKeys.size();
    }
}
