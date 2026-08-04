package com.tikitaka.bidwinback.upload.domain;

import com.tikitaka.bidwinback.upload.domain.entity.PendingAuctionImage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PendingAuctionImageStore {

    void saveAll(long memberId, UUID draftId, List<String> objectKeys);

    // 경매 등록 시 요청에 실린 objectKey가 실제로 이 회원의 이 draftId(등록 시도) 소속인지 확인하는 용도.
    // draftId까지 확인해야, 다른 세션(예: 이전에 등록을 포기한 draft)에 남아있던 objectKey가
    // 이번 등록 요청에 잘못 섞여 들어와도 걸러진다.
    List<PendingAuctionImage> findByMemberIdAndDraftIdAndObjectKeyIn(
            long memberId,
            UUID draftId,
            List<String> objectKeys
    );

    List<PendingAuctionImage> findExpiredBefore(LocalDateTime cutoff, int limit);

    void deleteByObjectKeyIn(List<String> objectKeys);
}
