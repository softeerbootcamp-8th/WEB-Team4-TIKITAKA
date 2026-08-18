package com.tikitaka.bidwinback.auction.application.live;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.tikitaka.bidwinback.auction.application.bid.BidHistoryService;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidHistoryResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/** 짧은 SSE 접속 폭주 동안 같은 revision의 입찰 내역 조회를 합친다. */
@Component
public class AuctionBidHistoryCache {

    private static final String CACHE_NAME = "auctionSseBidHistory";
    private static final int MAXIMUM_SIZE = 256;
    private static final Duration EXPIRE_AFTER_WRITE = Duration.ofMillis(500);

    private final AsyncLoadingCache<HistoryKey, BidHistoryResponse> cache;

    @Autowired
    public AuctionBidHistoryCache(
            BidHistoryService bidHistoryService,
            MeterRegistry meterRegistry,
            @Qualifier("auctionSnapshotTaskExecutor") Executor loaderExecutor
    ) {
        this(bidHistoryService, meterRegistry, loaderExecutor, Ticker.systemTicker());
    }

    AuctionBidHistoryCache(
            BidHistoryService bidHistoryService,
            MeterRegistry meterRegistry,
            Executor loaderExecutor,
            Ticker ticker
    ) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAXIMUM_SIZE)
                .expireAfterWrite(EXPIRE_AFTER_WRITE)
                .ticker(ticker)
                .executor(loaderExecutor)
                .recordStats()
                .buildAsync(new CacheLoader<>() {
                    @Override
                    public BidHistoryResponse load(HistoryKey key) {
                        return bidHistoryService.getBidHistory(
                                key.auctionId(),
                                key.status(),
                                key.bidCount()
                        );
                    }
                });
        CaffeineCacheMetrics.monitor(meterRegistry, cache.synchronous(), CACHE_NAME);
    }

    public BidHistoryResponse getHistory(AuctionLiveState state) {
        if (state.auctionType() != AuctionType.UP) {
            throw new IllegalArgumentException("상향 경매만 입찰 내역을 조회할 수 있습니다.");
        }
        return await(cache.get(HistoryKey.from(state)));
    }

    public void invalidate(long auctionId) {
        // 이전 revision의 완료되지 않은 load도 분리해 새 연결이 그 결과를 기다리지 않게 한다.
        cache.asMap().keySet().stream()
                .filter(key -> key.auctionId() == auctionId)
                .forEach(key -> cache.asMap().remove(key));
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            if (exception.getCause() instanceof Error cause) {
                throw cause;
            }
            throw exception;
        }
    }

    private record HistoryKey(
            long auctionId,
            long revision,
            AuctionStatus status,
            long bidCount
    ) {

        private static HistoryKey from(AuctionLiveState state) {
            return new HistoryKey(
                    state.auctionId(),
                    state.revision(),
                    state.status(),
                    state.bidCount()
            );
        }
    }
}
