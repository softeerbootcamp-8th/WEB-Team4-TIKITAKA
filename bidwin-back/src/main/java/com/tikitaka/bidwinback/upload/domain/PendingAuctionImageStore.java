package com.tikitaka.bidwinback.upload.domain;

import com.tikitaka.bidwinback.upload.domain.entity.PendingAuctionImage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PendingAuctionImageStore {

    void saveAll(long memberId, UUID draftId, List<String> objectKeys);

    // 경매 등록 시 요청에 실린 objectKey가 실제로 이 회원이 업로드한 이미지인지 확인하는 용도.
    List<PendingAuctionImage> findByMemberIdAndObjectKeyIn(long memberId, List<String> objectKeys);

    List<PendingAuctionImage> findExpiredBefore(LocalDateTime cutoff, int limit);

    void deleteByObjectKeyIn(List<String> objectKeys);
}
