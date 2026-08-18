package com.tikitaka.bidwinback.upload.infrastructure;

import com.tikitaka.bidwinback.upload.domain.AuctionImageUploadReservation;
import com.tikitaka.bidwinback.upload.domain.entity.PendingAuctionImage;
import com.tikitaka.bidwinback.upload.domain.repository.PendingAuctionImageStore;
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
import java.util.UUID;

public interface JpaPendingAuctionImageStore extends PendingAuctionImageStore, JpaRepository<PendingAuctionImage, Long> {

    @Override
    @Transactional
    default void saveAll(
            long memberId,
            UUID draftId,
            List<AuctionImageUploadReservation> reservations
    ) {
        List<PendingAuctionImage> list = reservations.stream()
                .map(reservation -> PendingAuctionImage.issue(
                        memberId,
                        draftId,
                        reservation.uploadId(),
                        reservation.objectKey(),
                        reservation.contentType(),
                        reservation.contentLength(),
                        reservation.checksumSha256()
                ))
                .toList();

        saveAll(list);
    }

    // 경매 등록 요청끼리, 그리고 만료 정리 작업과 예약 소비 순서를 직렬화한다.
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT image
            FROM PendingAuctionImage image
            WHERE image.memberId = :memberId
              AND image.draftId = :draftId
              AND image.uploadId IN :uploadIds
            """)
    List<PendingAuctionImage> findByMemberIdAndDraftIdAndUploadIdInForUpdate(
            @Param("memberId") long memberId,
            @Param("draftId") UUID draftId,
            @Param("uploadIds") List<UUID> uploadIds
    );

    @Override
    default List<PendingAuctionImage> findExpiredBefore(LocalDateTime cutoff, int limit) {
        return findByCreatedAtBeforeOrderByCreatedAtAscForUpdate(
                cutoff,
                PageRequest.of(0, limit)
        );
    }

    // 등록 중인 예약을 만료 정리가 삭제하지 않도록 등록 조회와 동일한 쓰기 락을 사용한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT image
            FROM PendingAuctionImage image
            WHERE image.createdAt < :cutoff
            ORDER BY image.createdAt ASC
            """)
    List<PendingAuctionImage> findByCreatedAtBeforeOrderByCreatedAtAscForUpdate(
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable
    );

    @Override
    @Transactional
    void deleteByObjectKeyIn(List<String> objectKeys);
}
