package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionListStatusFilter;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import com.tikitaka.bidwinback.auction.infrastructure.RedisSnapshotStore;
import com.tikitaka.bidwinback.auction.infrastructure.RedisSnapshotUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
public class DownPriceSnapshotResolver {

    static final int PAGE_SIZE = 16;
    static final int MAX_PAGES = 100;

    private final RedisSnapshotStore redisStore;
    private final LocalSnapshotStore localStore;
    private final DownPriceSnapshotBuildCoordinator buildCoordinator;
    private final AuctionDatabaseTimeQuery databaseTimeQuery;
    private final DownPriceSnapshotMetrics metrics;
    private final Duration refreshInterval;
    private final Duration retention;

    public DownPriceSnapshotResolver(
            RedisSnapshotStore redisStore,
            LocalSnapshotStore localStore,
            DownPriceSnapshotBuildCoordinator buildCoordinator,
            AuctionDatabaseTimeQuery databaseTimeQuery,
            DownPriceSnapshotMetrics metrics,
            @Value("${app.auction.down-price-snapshot.refresh-interval}")
            Duration refreshInterval,
            @Value("${app.auction.down-price-snapshot.retention}") Duration retention
    ) {
        this.redisStore = redisStore;
        this.localStore = localStore;
        this.buildCoordinator = buildCoordinator;
        this.databaseTimeQuery = databaseTimeQuery;
        this.metrics = metrics;
        this.refreshInterval = refreshInterval;
        this.retention = retention;
    }

    public boolean supports(AuctionListQuery query) {
        return query.auctionType() == AuctionType.DOWN
                && (query.status() == null || query.status() == AuctionListStatusFilter.ACTIVE)
                && (query.sort() == AuctionSort.PRICE_LOW
                || query.sort() == AuctionSort.PRICE_HIGH)
                && query.keyword() == null
                && query.category() == null
                && query.size() == PAGE_SIZE
                && query.page() >= 1
                && query.page() <= MAX_PAGES;
    }

    public CompletableFuture<ResolvedSnapshot> resolve(AuctionListQuery query) {
        if (!supports(query)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("스냅샷 캐시를 지원하지 않는 목록 요청입니다.")
            );
        }
        LocalDateTime serverTime = databaseTimeQuery.currentTime();
        if (query.asOf() == null) {
            return resolveLatest(query.sort(), query.page(), serverTime, false);
        }
        return resolveExact(query, serverTime);
    }

    private CompletableFuture<ResolvedSnapshot> resolveExact(
            AuctionListQuery query,
            LocalDateTime serverTime
    ) {
        LocalDateTime generationAt = query.asOf();
        if (!generationAt.equals(SnapshotBuildKey.latestSlot(generationAt).generationAt())
                || generationAt.isAfter(serverTime)
                || Duration.between(generationAt, serverTime).compareTo(retention) > 0) {
            return resetToLatest(query.sort(), serverTime);
        }

        Optional<DownPriceSnapshot> local = localStore.find(generationAt);
        if (local.isPresent()) {
            metrics.recordLookup("local", "hit");
            return CompletableFuture.completedFuture(resolved(
                    page(local.get(), query.sort(), query.page()),
                    serverTime,
                    query.page(),
                    false
            ));
        }
        metrics.recordLookup("local", "miss");

        try {
            Optional<SnapshotGenerationPage> redisPage = redisStore.findExactPage(
                    generationAt,
                    query.sort(),
                    query.page(),
                    PAGE_SIZE
            );
            if (redisPage.isPresent()) {
                metrics.recordLookup("redis", "hit");
                return CompletableFuture.completedFuture(resolved(
                        redisPage.get(),
                        serverTime,
                        query.page(),
                        false
                ));
            }
            metrics.recordLookup("redis", "miss");
            return resetToLatest(query.sort(), serverTime);
        } catch (RedisSnapshotUnavailableException exception) {
            metrics.recordLookup("redis", "error");
            return build(
                    SnapshotBuildKey.exact(generationAt),
                    query.sort(),
                    query.page(),
                    serverTime,
                    false
            );
        }
    }

    private CompletableFuture<ResolvedSnapshot> resetToLatest(
            AuctionSort sort,
            LocalDateTime serverTime
    ) {
        metrics.recordReset("expired");
        return resolveLatest(sort, 1, serverTime, true);
    }

    private CompletableFuture<ResolvedSnapshot> resolveLatest(
            AuctionSort sort,
            int page,
            LocalDateTime serverTime,
            boolean reset
    ) {
        try {
            Optional<SnapshotGenerationPage> redisPage = redisStore.findLatestPage(
                    sort,
                    page,
                    PAGE_SIZE
            );
            if (redisPage.isPresent()) {
                metrics.recordLookup("redis", "hit");
                return CompletableFuture.completedFuture(resolved(
                        redisPage.get(),
                        serverTime,
                        page,
                        reset
                ));
            }
            metrics.recordLookup("redis", "miss");
        } catch (RedisSnapshotUnavailableException exception) {
            metrics.recordLookup("redis", "error");
            Optional<DownPriceSnapshot> local = localStore.findLatest()
                    .filter(snapshot -> isFresh(snapshot.generationAt(), serverTime));
            if (local.isPresent()) {
                metrics.recordLookup("local", "hit");
                return CompletableFuture.completedFuture(resolved(
                        page(local.get(), sort, page),
                        serverTime,
                        page,
                        reset
                ));
            }
            metrics.recordLookup("local", "miss");
        }

        return build(
                SnapshotBuildKey.latestSlot(serverTime),
                sort,
                page,
                serverTime,
                reset
        );
    }

    private CompletableFuture<ResolvedSnapshot> build(
            SnapshotBuildKey key,
            AuctionSort sort,
            int page,
            LocalDateTime serverTime,
            boolean reset
    ) {
        return buildCoordinator.getOrBuild(key)
                .thenApply(snapshot -> {
                    metrics.recordLookup("db", "hit");
                    return resolved(page(snapshot, sort, page), serverTime, page, reset);
                });
    }

    private ResolvedSnapshot resolved(
            SnapshotGenerationPage page,
            LocalDateTime serverTime,
            int effectivePage,
            boolean reset
    ) {
        metrics.recordGenerationAge(Duration.between(page.generationAt(), serverTime));
        return new ResolvedSnapshot(
                page,
                serverTime,
                effectivePage,
                reset,
                reset ? SnapshotResetReason.GENERATION_EXPIRED : null
        );
    }

    private SnapshotGenerationPage page(
            DownPriceSnapshot snapshot,
            AuctionSort sort,
            int page
    ) {
        List<AuctionPriceSnapshot> entries = snapshot.entries(sort);
        int fromIndex = Math.min((page - 1) * PAGE_SIZE, entries.size());
        int toIndex = Math.min(fromIndex + PAGE_SIZE, entries.size());
        return new SnapshotGenerationPage(
                snapshot.generationAt(),
                entries.size(),
                entries.subList(fromIndex, toIndex)
        );
    }

    private boolean isFresh(LocalDateTime generationAt, LocalDateTime serverTime) {
        Duration age = Duration.between(generationAt, serverTime);
        return !age.isNegative() && age.compareTo(refreshInterval) <= 0;
    }
}
