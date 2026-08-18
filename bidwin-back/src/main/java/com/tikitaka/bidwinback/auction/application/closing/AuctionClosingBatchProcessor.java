package com.tikitaka.bidwinback.auction.application.closing;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

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
                log.atWarn()
                        .setCause(exception)
                        .addKeyValue("event", "auction_closing_batch_failed_fallback_to_individual")
                        .addKeyValue("status", status)
                        .log("경매 마감 배치 실패, 개별 재처리로 전환");
                closeIndividually(status, MAX_BATCHES_PER_RUN - batch);
                return;
            }
            if (closed == 0) {
                return;
            }
        }
    }

    private void closeIndividually(AuctionStatus status, int remainingBatches) {
        List<Long> candidateIds;
        try {
            int candidateLimit = Math.multiplyExact(batchSize, remainingBatches);
            candidateIds = auctionClosingService.findClosingCandidateIds(
                    status,
                    candidateLimit
            );
        } catch (RuntimeException exception) {
            log.atWarn()
                    .setCause(exception)
                    .addKeyValue("event", "auction_closing_individual_retry_preparation_failed")
                    .addKeyValue("status", status)
                    .log("경매 마감 개별 재처리 준비 실패");
            return;
        }

        for (Long auctionId : candidateIds) {
            try {
                auctionClosingService.closeOne(status, auctionId);
            } catch (RuntimeException exception) {
                log.atWarn()
                        .setCause(exception)
                        .addKeyValue("event", "auction_closing_individual_failed")
                        .addKeyValue("status", status)
                        .addKeyValue("auctionId", auctionId)
                        .log("경매 마감 개별 처리 실패");
            }
        }
    }
}
