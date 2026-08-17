package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.AuctionClosingBatchProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuctionClosingSchedulerTest {

    @Mock
    private AuctionClosingBatchProcessor batchProcessor;

    private AuctionClosingScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AuctionClosingScheduler(
                batchProcessor
        );
    }

    @Test
    void 자동_마감_주기가_오면_마감_배치를_한_번_실행한다() {
        // given

        // when
        scheduler.closeEndedAuctions();

        // then
        verify(batchProcessor).closeEndedAuctions();
    }
}
