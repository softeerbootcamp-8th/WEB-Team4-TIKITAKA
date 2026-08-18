package com.tikitaka.bidwinback.upload.application;

import com.tikitaka.bidwinback.global.config.PendingProfileImageProperties;
import com.tikitaka.bidwinback.global.storage.ObjectDeletionResult;
import com.tikitaka.bidwinback.global.storage.ObjectStorage;
import com.tikitaka.bidwinback.upload.domain.entity.PendingProfileImage;
import com.tikitaka.bidwinback.upload.domain.repository.PendingProfileImageStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingProfileImageCleanupService {

    private final PendingProfileImageStore pendingProfileImageStore;
    private final ObjectStorage objectStorage;
    private final PendingProfileImageProperties cleanupProperties;
    private final Clock clock;

    @Transactional
    public int cleanup() {
        LocalDateTime cutoff = LocalDateTime.ofInstant(
                clock.instant().minus(cleanupProperties.retention()),
                ZoneId.systemDefault()
        );
        // 활성화 검증과 같은 행을 잠가 사용 예정 이미지를 삭제하지 않는다.
        List<PendingProfileImage> expiredImages =
                pendingProfileImageStore.findExpiredBeforeForUpdate(
                        cutoff,
                        cleanupProperties.cleanupBatchSize()
                );

        if (expiredImages.isEmpty()) {
            return 0;
        }

        List<String> expiredKeys = expiredImages.stream()
                .map(PendingProfileImage::getObjectKey)
                .toList();
        Map<String, Long> memberIdByObjectKey = expiredImages.stream()
                .collect(toMap(
                        PendingProfileImage::getObjectKey,
                        PendingProfileImage::getMemberId
                ));
        ObjectDeletionResult result = objectStorage.deleteAll(expiredKeys);

        result.failures().forEach(failure -> log.atWarn()
                .addKeyValue("event", "pending_profile_image_cleanup_delete_failed")
                .addKeyValue("memberId", memberIdByObjectKey.get(failure.objectKey()))
                .addKeyValue("failureCode", failure.code())
                .log("미사용 프로필 이미지 삭제 실패"));

        if (!result.deletedKeys().isEmpty()) {
            pendingProfileImageStore.deleteByObjectKeyIn(result.deletedKeys());
        }
        return result.deletedKeys().size();
    }
}
