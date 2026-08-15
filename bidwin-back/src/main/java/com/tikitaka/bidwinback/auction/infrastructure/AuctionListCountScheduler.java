package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.AuctionListCountCache;
import com.tikitaka.bidwinback.auction.application.AuctionListCountSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionListCountScheduler {

    private final AuctionListCountSnapshotService snapshotService;
    private final AuctionListCountCache countCache;

    @Scheduled(
            fixedDelayString = "${app.auction.list-count-cache-refresh-interval}",
            initialDelay = 0
    )
    public void refresh() {
        try {
            countCache.publish(snapshotService.capture());
        } catch (RuntimeException exception) {
            log.error("경매 목록 count 캐시 갱신 실패", exception);
        }
    }
}
