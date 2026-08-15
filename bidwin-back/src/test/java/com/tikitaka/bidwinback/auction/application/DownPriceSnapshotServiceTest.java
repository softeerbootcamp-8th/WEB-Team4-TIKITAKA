package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.DownPriceSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DownPriceSnapshotServiceTest {

    private static final LocalDateTime DATABASE_TIME =
            LocalDateTime.of(2026, 8, 14, 12, 0, 0, 123_456_000);

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private DownPriceSnapshotRepository downPriceSnapshotRepository;

    private DownPriceSnapshotService downPriceSnapshotService;

    @BeforeEach
    void setUp() {
        downPriceSnapshotService = new DownPriceSnapshotService(
                auctionRepository,
                downPriceSnapshotRepository
        );
        when(auctionRepository.currentDatabaseTime()).thenReturn(DATABASE_TIME);
    }

    @Test
    void DB_현재시각을_밀리초로_절삭해_스냅샷을_적재한다() {
        LocalDateTime snapshotAt = DATABASE_TIME.truncatedTo(ChronoUnit.MILLIS);
        when(downPriceSnapshotRepository.capture(snapshotAt)).thenReturn(3);

        DownPriceSnapshotService.CaptureResult result = downPriceSnapshotService.capture();

        assertThat(result.snapshotAt()).isEqualTo(snapshotAt);
        assertThat(result.count()).isEqualTo(3);
        verify(downPriceSnapshotRepository).capture(snapshotAt);
    }

    @Test
    void DB_현재시각을_밀리초로_절삭한_뒤_보존기간을_빼서_정리한다() {
        Duration retention = Duration.ofMinutes(10);
        LocalDateTime threshold = DATABASE_TIME
                .truncatedTo(ChronoUnit.MILLIS)
                .minus(retention);
        when(downPriceSnapshotRepository.deleteOlderThan(threshold)).thenReturn(2);

        int deletedCount = downPriceSnapshotService.deleteOlderThan(retention);

        assertThat(deletedCount).isEqualTo(2);
        verify(downPriceSnapshotRepository).deleteOlderThan(threshold);
    }
}
