package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.AuctionClosingService;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionClosingScheduler {

    private final AuctionRepository auctionRepository;
    private final AuctionClosingService auctionClosingService;

    @Scheduled(
            fixedDelayString = "${app.auction.closing-interval}",
            initialDelayString = "${app.auction.closing-interval}"
    )
    public void closeEndedAuctions() {
        for (Long auctionId : auctionRepository.findClosingCandidateIds()) {
            try {
                auctionClosingService.closeIfAvailable(auctionId);
            } catch (RuntimeException exception) {
                log.error("경매 마감 실패: auctionId={}", auctionId, exception);
            }
        }
    }
}
