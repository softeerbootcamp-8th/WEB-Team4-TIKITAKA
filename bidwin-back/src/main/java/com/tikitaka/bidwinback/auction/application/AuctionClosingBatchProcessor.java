package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuctionClosingBatchProcessor {

    private static final int MAX_BATCHES_PER_RUN = 100;

    private final AuctionClosingService auctionClosingService;
    private final int batchSize;

    public AuctionClosingBatchProcessor(
            AuctionClosingService auctionClosingService,
            @Value("${app.auction.closing-batch-size}") int batchSize
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("경매 마감 배치 크기는 양수여야 합니다.");
        }
        this.auctionClosingService = auctionClosingService;
        this.batchSize = batchSize;
    }

    public void closeEndedAuctions() {
        closeCandidates(AuctionStatus.OPEN);
        closeCandidates(AuctionStatus.BID_ONGOING);
    }

    private void closeCandidates(AuctionStatus status) {
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int closed;
            try {
                closed = auctionClosingService.closeBatch(status, batchSize);
            } catch (RuntimeException exception) {
                log.error("경매 마감 배치 실패: status={}", status, exception);
                return;
            }
            if (closed == 0) {
                return;
            }
        }
    }
}
