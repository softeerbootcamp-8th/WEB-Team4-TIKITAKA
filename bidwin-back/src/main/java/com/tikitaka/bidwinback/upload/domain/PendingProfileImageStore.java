package com.tikitaka.bidwinback.upload.domain;

import com.tikitaka.bidwinback.upload.domain.entity.PendingProfileImage;

import java.util.List;
import java.util.Optional;

public interface PendingProfileImageStore {

    void save(long memberId, String objectKey);

    Optional<PendingProfileImage> findByMemberIdAndObjectKey(
            long memberId,
            String objectKey
    );

    void deleteByObjectKeyIn(List<String> objectKeys);
}
