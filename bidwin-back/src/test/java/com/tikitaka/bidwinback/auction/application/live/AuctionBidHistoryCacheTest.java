package com.tikitaka.bidwinback.auction.application.live;

import com.github.benmanes.caffeine.cache.Ticker;
import com.tikitaka.bidwinback.auction.application.bid.BidHistoryService;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidHistoryResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionBidHistoryCacheTest {

    @Mock
    private BidHistoryService bidHistoryService;

    private MutableTicker ticker;
    private AuctionBidHistoryCache historyCache;
    private ExecutorService loaderExecutor;

    @BeforeEach
    void setUp() {
        ticker = new MutableTicker();
        loaderExecutor = Executors.newFixedThreadPool(4);
        historyCache = new AuctionBidHistoryCache(
                bidHistoryService,
                new SimpleMeterRegistry(),
                loaderExecutor,
                ticker
        );
    }

    @AfterEach
    void tearDown() {
        loaderExecutor.shutdownNow();
    }

    @Test
    void 동시에_같은_revision을_조회하면_입찰내역을_한번만_읽는다() throws Exception {
        // given
        int connectionCount = 20;
        AuctionLiveState state = state(1L, 3L, 3L);
        BidHistoryResponse history = new BidHistoryResponse(3L, List.of());
        CountDownLatch callersReady = new CountDownLatch(connectionCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch loadStarted = new CountDownLatch(1);
        CountDownLatch releaseLoad = new CountDownLatch(1);
        when(bidHistoryService.getBidHistory(1L, state.status(), 3L))
                .thenAnswer(ignored -> {
                    loadStarted.countDown();
                    releaseLoad.await(5, TimeUnit.SECONDS);
                    return history;
                });

        try (ExecutorService executor = Executors.newFixedThreadPool(connectionCount)) {
            List<Future<BidHistoryResponse>> futures = java.util.stream.IntStream
                    .range(0, connectionCount)
                    .mapToObj(ignored -> executor.submit(() -> {
                        callersReady.countDown();
                        start.await();
                        return historyCache.getHistory(state);
                    }))
                    .toList();
            callersReady.await(5, TimeUnit.SECONDS);

            // when
            start.countDown();
            loadStarted.await(5, TimeUnit.SECONDS);
            releaseLoad.countDown();

            // then
            for (Future<BidHistoryResponse> future : futures) {
                assertThat(future.get(5, TimeUnit.SECONDS)).isSameAs(history);
            }
        }
        verify(bidHistoryService).getBidHistory(1L, state.status(), 3L);
    }

    @Test
    void revision이_바뀌면_만료_전에도_새_입찰내역을_읽는다() {
        // given
        AuctionLiveState oldState = state(1L, 1L, 1L);
        AuctionLiveState newState = state(1L, 2L, 2L);
        BidHistoryResponse oldHistory = new BidHistoryResponse(1L, List.of());
        BidHistoryResponse newHistory = new BidHistoryResponse(2L, List.of());
        when(bidHistoryService.getBidHistory(1L, oldState.status(), 1L))
                .thenReturn(oldHistory);
        when(bidHistoryService.getBidHistory(1L, newState.status(), 2L))
                .thenReturn(newHistory);

        // when
        BidHistoryResponse first = historyCache.getHistory(oldState);
        BidHistoryResponse second = historyCache.getHistory(newState);

        // then
        assertThat(first).isSameAs(oldHistory);
        assertThat(second).isSameAs(newHistory);
        verify(bidHistoryService).getBidHistory(1L, oldState.status(), 1L);
        verify(bidHistoryService).getBidHistory(1L, newState.status(), 2L);
    }

    @Test
    void 무효화와_겹친_이전_조회가_완료돼도_새_cache를_덮지_않는다() throws Exception {
        // given
        AuctionLiveState state = state(1L, 1L, 1L);
        BidHistoryResponse oldHistory = new BidHistoryResponse(1L, List.of());
        BidHistoryResponse newHistory = new BidHistoryResponse(1L, List.of());
        AtomicInteger loadCount = new AtomicInteger();
        CountDownLatch oldLoadStarted = new CountDownLatch(1);
        CountDownLatch releaseOldLoad = new CountDownLatch(1);
        when(bidHistoryService.getBidHistory(1L, state.status(), 1L))
                .thenAnswer(ignored -> {
                    if (loadCount.incrementAndGet() == 1) {
                        oldLoadStarted.countDown();
                        releaseOldLoad.await(5, TimeUnit.SECONDS);
                        return oldHistory;
                    }
                    return newHistory;
                });

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<BidHistoryResponse> oldResult = executor.submit(
                    () -> historyCache.getHistory(state)
            );
            oldLoadStarted.await(5, TimeUnit.SECONDS);

            // when
            historyCache.invalidate(1L);
            Future<BidHistoryResponse> newResult = executor.submit(
                    () -> historyCache.getHistory(state)
            );
            assertThat(newResult.get(5, TimeUnit.SECONDS)).isSameAs(newHistory);
            releaseOldLoad.countDown();
            assertThat(oldResult.get(5, TimeUnit.SECONDS)).isSameAs(oldHistory);

            // then
            assertThat(historyCache.getHistory(state)).isSameAs(newHistory);
        } finally {
            releaseOldLoad.countDown();
        }
    }

    @Test
    void 입찰내역은_500밀리초가_지나면_다시_읽는다() {
        // given
        AuctionBidHistoryCache expiringCache = new AuctionBidHistoryCache(
                bidHistoryService,
                new SimpleMeterRegistry(),
                Runnable::run,
                ticker
        );
        AuctionLiveState state = state(1L, 1L, 1L);
        BidHistoryResponse oldHistory = new BidHistoryResponse(1L, List.of());
        BidHistoryResponse newHistory = new BidHistoryResponse(1L, List.of());
        AtomicInteger loadCount = new AtomicInteger();
        when(bidHistoryService.getBidHistory(1L, state.status(), 1L))
                .thenAnswer(ignored -> loadCount.getAndIncrement() == 0
                        ? oldHistory
                        : newHistory);
        expiringCache.getHistory(state);

        // when
        ticker.advance(Duration.ofMillis(501));
        BidHistoryResponse refreshed = expiringCache.getHistory(state);

        // then
        assertThat(refreshed).isSameAs(newHistory);
    }

    @Test
    void 하향경매는_입찰내역_cache를_사용하지_않는다() {
        // given
        AuctionLiveState downState = new AuctionLiveState(
                1L,
                1L,
                AuctionType.DOWN,
                AuctionStatus.OPEN,
                100_000L,
                0L
        );

        // when & then
        assertThatThrownBy(() -> historyCache.getHistory(downState))
                .isInstanceOf(IllegalArgumentException.class);
        verify(bidHistoryService, never()).getBidHistory(1L, downState.status(), 0L);
    }

    private AuctionLiveState state(long auctionId, long revision, long bidCount) {
        return new AuctionLiveState(
                auctionId,
                revision,
                AuctionType.UP,
                AuctionStatus.BID_ONGOING,
                120_000L + revision,
                bidCount
        );
    }

    private static final class MutableTicker implements Ticker {

        private final AtomicLong nanos = new AtomicLong(1L);

        @Override
        public long read() {
            return nanos.get();
        }

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }
    }
}
