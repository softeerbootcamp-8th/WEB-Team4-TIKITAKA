package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionClosingBatchProcessor {

    private final AuctionClosingService auctionClosingService;

    public void closeEndedAuctions() {
        closeCandidates(AuctionStatus.OPEN);
        closeCandidates(AuctionStatus.BID_ONGOING);
    }

    private void closeCandidates(AuctionStatus status) {
        for (int attempt = 0; attempt < 100; attempt++) {
            try {
                if (!auctionClosingService.closeOneCandidate(status)) {
                    break;
                }
            } catch (RuntimeException exception) {
                log.error("경매 마감 실패: status={}", status, exception);
            }
        }
    }
}
