package com.tikitaka.bidwinback.upload.infrastructure;

import com.tikitaka.bidwinback.upload.domain.entity.PendingProfileImage;
import com.tikitaka.bidwinback.upload.domain.repository.PendingProfileImageStore;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JpaPendingProfileImageStore extends
        PendingProfileImageStore,
        JpaRepository<PendingProfileImage, Long> {

    @Override
    @Transactional
    default void save(long memberId, String objectKey) {
        save(PendingProfileImage.issue(memberId, objectKey));
    }

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT image
            FROM PendingProfileImage image
            WHERE image.memberId = :memberId
              AND image.objectKey = :objectKey
            """)
    Optional<PendingProfileImage> findByMemberIdAndObjectKeyForUpdate(
            @Param("memberId") long memberId,
            @Param("objectKey") String objectKey
    );

    @Override
    default List<PendingProfileImage> findExpiredBeforeForUpdate(
            LocalDateTime cutoff,
            int limit
    ) {
        return findByCreatedAtBeforeOrderByCreatedAtAscForUpdate(
                cutoff,
                PageRequest.of(0, limit)
        );
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT image
            FROM PendingProfileImage image
            WHERE image.createdAt < :cutoff
            ORDER BY image.createdAt ASC
            """)
    List<PendingProfileImage> findByCreatedAtBeforeOrderByCreatedAtAscForUpdate(
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable
    );

    @Override
    @Transactional
    void deleteByObjectKeyIn(List<String> objectKeys);
}
