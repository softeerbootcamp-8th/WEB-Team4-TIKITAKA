package com.tikitaka.bidwinback.upload.domain;

import com.tikitaka.bidwinback.upload.domain.entity.PendingProfileImage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PendingProfileImageStore {

    void save(long memberId, String objectKey);

    Optional<PendingProfileImage> findByMemberIdAndObjectKeyForUpdate(
            long memberId,
            String objectKey
    );

    List<PendingProfileImage> findExpiredBeforeForUpdate(
            LocalDateTime cutoff,
            int limit
    );

    void deleteByObjectKeyIn(List<String> objectKeys);
}
