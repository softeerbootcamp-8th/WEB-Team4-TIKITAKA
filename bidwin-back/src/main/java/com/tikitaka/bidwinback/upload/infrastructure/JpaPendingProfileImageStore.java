package com.tikitaka.bidwinback.upload.infrastructure;

import com.tikitaka.bidwinback.upload.domain.PendingProfileImageStore;
import com.tikitaka.bidwinback.upload.domain.entity.PendingProfileImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface JpaPendingProfileImageStore extends
        PendingProfileImageStore,
        JpaRepository<PendingProfileImage, Long> {

    @Override
    @Transactional
    default void save(long memberId, String objectKey) {
        save(PendingProfileImage.issue(memberId, objectKey));
    }

    @Override
    @Transactional
    void deleteByObjectKeyIn(List<String> objectKeys);
}
