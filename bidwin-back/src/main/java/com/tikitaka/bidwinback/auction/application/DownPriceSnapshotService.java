package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.DownPriceSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class DownPriceSnapshotService {

    private final AuctionRepository auctionRepository;
    private final DownPriceSnapshotRepository downPriceSnapshotRepository;

    @Transactional
    public CaptureResult capture() {
        LocalDateTime snapshotAt = currentDatabaseTime();
        int count = downPriceSnapshotRepository.capture(snapshotAt);
        return new CaptureResult(snapshotAt, count);
    }

    @Transactional
    public int deleteOlderThan(Duration retention) {
        LocalDateTime threshold = currentDatabaseTime().minus(retention);
        return downPriceSnapshotRepository.deleteOlderThan(threshold);
    }

    private LocalDateTime currentDatabaseTime() {
        return auctionRepository.currentDatabaseTime().truncatedTo(ChronoUnit.MILLIS);
    }

    public record CaptureResult(LocalDateTime snapshotAt, int count) {
    }
}
