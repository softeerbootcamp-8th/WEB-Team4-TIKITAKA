package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.closing.AuctionClosingBatchProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionClosingScheduler {

    private final AuctionClosingBatchProcessor batchProcessor;

    @Scheduled(
            fixedDelayString = "${app.auction.closing-interval}",
            initialDelayString = "${app.auction.closing-interval}"
    )
    public void closeEndedAuctions() {
        batchProcessor.closeEndedAuctions();
    }
}
