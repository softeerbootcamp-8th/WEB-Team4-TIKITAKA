package com.tikitaka.bidwinback.upload.domain;

import com.tikitaka.bidwinback.upload.domain.entity.PendingAuctionImage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PendingAuctionImageStore {

    void saveAll(long memberId, UUID draftId, List<String> objectKeys);

    List<PendingAuctionImage> findExpiredBefore(LocalDateTime cutoff, int limit);

    void deleteByObjectKeyIn(List<String> objectKeys);
}
