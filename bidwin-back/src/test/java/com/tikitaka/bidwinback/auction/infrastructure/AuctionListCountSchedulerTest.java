package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.application.AuctionListCountCache;
import com.tikitaka.bidwinback.auction.application.AuctionListCountSnapshotService;
import com.tikitaka.bidwinback.auction.application.AuctionListCounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionListCountSchedulerTest {

    @Mock
    private AuctionListCountSnapshotService snapshotService;

    @Mock
    private AuctionListCountCache countCache;

    private AuctionListCountScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AuctionListCountScheduler(snapshotService, countCache);
    }

    @Test
    void snapshot을_생성한_후_count_캐시에_publish한다() {
        AuctionListCounts counts = new AuctionListCounts(30L, 10L, 20L);
        when(snapshotService.capture()).thenReturn(counts);

        scheduler.refresh();

        InOrder inOrder = inOrder(snapshotService, countCache);
        inOrder.verify(snapshotService).capture();
        inOrder.verify(countCache).publish(counts);
    }

    @Test
    void capture가_실패해도_예외를_전파하지_않고_다음_실행에서_갱신한다() {
        AuctionListCounts counts = new AuctionListCounts(30L, 10L, 20L);
        when(snapshotService.capture())
                .thenThrow(new IllegalStateException("database unavailable"))
                .thenReturn(counts);

        assertThatCode(scheduler::refresh).doesNotThrowAnyException();
        assertThatCode(scheduler::refresh).doesNotThrowAnyException();

        verify(snapshotService, times(2)).capture();
        verify(countCache).publish(counts);
    }

    @Test
    void publish가_실패해도_예외를_전파하지_않는다() {
        AuctionListCounts counts = new AuctionListCounts(30L, 10L, 20L);
        when(snapshotService.capture()).thenReturn(counts);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(countCache)
                .publish(counts);

        assertThatCode(scheduler::refresh).doesNotThrowAnyException();
    }
}
