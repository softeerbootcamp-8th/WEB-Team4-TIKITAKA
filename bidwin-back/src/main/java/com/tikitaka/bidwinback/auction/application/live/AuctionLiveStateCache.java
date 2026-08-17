package com.tikitaka.bidwinback.auction.application.live;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 짧은 접속 폭주 동안 동일한 경매 snapshot 조회를 합친다. */
@Component
public class AuctionLiveStateCache {

    private static final String CACHE_NAME = "auctionSseSnapshot";
    private static final int MAXIMUM_SIZE = 256;
    private static final Duration EXPIRE_AFTER_WRITE = Duration.ofMillis(500);

    private final AsyncLoadingCache<SnapshotKey, Map<Long, AuctionLiveState>> cache;

    @Autowired
    public AuctionLiveStateCache(
            AuctionLiveStateService stateService,
            MeterRegistry meterRegistry,
            @Qualifier("auctionSnapshotTaskExecutor") Executor loaderExecutor
    ) {
        this(stateService, meterRegistry, loaderExecutor, Ticker.systemTicker());
    }

    AuctionLiveStateCache(
            AuctionLiveStateService stateService,
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
                    public Map<Long, AuctionLiveState> load(SnapshotKey key) {
                        return stateService.getStates(key.auctionIds()).stream()
                                .collect(Collectors.toUnmodifiableMap(
                                        AuctionLiveState::auctionId,
                                        Function.identity()
                                ));
                    }
                });
        CaffeineCacheMetrics.monitor(meterRegistry, cache.synchronous(), CACHE_NAME);
    }

    public AuctionLiveState getState(long auctionId) {
        return getStates(List.of(auctionId)).getFirst();
    }

    public List<AuctionLiveState> getStates(Collection<Long> auctionIds) {
        List<Long> distinctIds = auctionIds.stream().distinct().toList();
        SnapshotKey key = SnapshotKey.from(distinctIds);
        Map<Long, AuctionLiveState> statesById = await(cache.get(key));
        return distinctIds.stream()
                .map(statesById::get)
                .toList();
    }

    public void invalidate(long auctionId) {
        // 완료되지 않은 load도 즉시 분리해야 AFTER_COMMIT 스레드가 DB 연결을 기다리지 않는다.
        // ponytail: 최대 256개인 짧은 cache를 선형 탐색한다. 키 수를 늘릴 때만 역색인을 추가한다.
        cache.asMap().keySet().stream()
                .filter(key -> key.auctionIds().contains(auctionId))
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

    private record SnapshotKey(List<Long> auctionIds) {

        private static SnapshotKey from(Collection<Long> auctionIds) {
            return new SnapshotKey(auctionIds.stream().sorted().toList());
        }
    }
}
