package com.tikitaka.bidwinback.auction.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionClosingBatchProcessor {

    private final AuctionClosingService auctionClosingService;

    public void closeEndedAuctions() {
        for (int attempt = 0; attempt < 100; attempt++) {
            try {
                auctionClosingService.closeOneCandidate();
            } catch (RuntimeException exception) {
                log.error("경매 마감 실패", exception);
            }
        }
    }
}
