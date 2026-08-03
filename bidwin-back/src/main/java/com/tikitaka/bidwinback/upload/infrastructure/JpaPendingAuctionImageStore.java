package com.tikitaka.bidwinback.upload.infrastructure;

import com.tikitaka.bidwinback.upload.domain.PendingAuctionImageStore;
import com.tikitaka.bidwinback.upload.domain.entity.PendingAuctionImage;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface JpaPendingAuctionImageStore extends PendingAuctionImageStore, JpaRepository<PendingAuctionImage, Long> {

    @Override
    @Transactional
    default void saveAll(long memberId, UUID draftId, List<String> objectKeys) {
        List<PendingAuctionImage> list = objectKeys.stream()
                .map(objectKey -> PendingAuctionImage.issue(memberId, draftId, objectKey))
                .toList();

        saveAll(list);
    }

    @Override
    default List<PendingAuctionImage> findExpiredBefore(LocalDateTime cutoff, int limit) {
        return findByCreatedAtBeforeOrderByCreatedAtAsc(
                cutoff,
                PageRequest.of(0, limit)
        );
    }

    List<PendingAuctionImage> findByCreatedAtBeforeOrderByCreatedAtAsc(
            LocalDateTime cutoff,
            Pageable pageable
    );

    @Override
    @Transactional
    void deleteByObjectKeyIn(List<String> objectKeys);
}
