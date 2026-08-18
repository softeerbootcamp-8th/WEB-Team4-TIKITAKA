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

    private final RedisSnapshotStore redisStore;
    private final DownPriceSnapshotBuildCoordinator buildCoordinator;
    private final AuctionDatabaseTimeQuery databaseTimeQuery;
    private final DownPriceSnapshotMetrics metrics;
    private final Duration retention;

    public DownPriceSnapshotResolver(
            RedisSnapshotStore redisStore,
            DownPriceSnapshotBuildCoordinator buildCoordinator,
            AuctionDatabaseTimeQuery databaseTimeQuery,
            DownPriceSnapshotMetrics metrics,
            @Value("${app.auction.down-price-snapshot.retention}") Duration retention
    ) {
        this.redisStore = redisStore;
        this.buildCoordinator = buildCoordinator;
        this.databaseTimeQuery = databaseTimeQuery;
        this.metrics = metrics;
        this.retention = retention;
    }

    public boolean supports(AuctionListQuery query) {
        return query.auctionType() == AuctionType.DOWN
                && (query.status() == null || query.status() == AuctionListStatusFilter.ACTIVE)
                && (query.sort() == AuctionSort.PRICE_LOW
                    || query.sort() == AuctionSort.PRICE_HIGH)
                && query.keyword() == null
                && query.category() == null
                && query.page() >= 1
                && query.page() <= AuctionListPagePolicy.MAX_PAGES;
    }

    public CompletableFuture<ResolvedDownPriceSnapshotPage> resolve(AuctionListQuery query) {
        if (!supports(query)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("스냅샷 캐시를 지원하지 않는 목록 요청입니다.")
            );
        }
        LocalDateTime serverTime = databaseTimeQuery.currentTime();
        if (query.asOf() == null) {
            return resolveLatest(query.sort(), query.page(), serverTime, null);
        }
        return resolveExact(query, serverTime);
    }

    private CompletableFuture<ResolvedDownPriceSnapshotPage> resolveExact(
            AuctionListQuery query,
            LocalDateTime serverTime
    ) {
        LocalDateTime generationAt = query.asOf();
        if (!DownPriceSnapshotBuildKey.isGenerationSlot(generationAt)
                || generationAt.isAfter(serverTime)
                || Duration.between(generationAt, serverTime).compareTo(retention) >= 0) {
            return resetToLatest(query.sort(), serverTime);
        }

        try {
            Optional<DownPriceSnapshotPage> redisPage = redisStore.findExactPage(
                    generationAt,
                    query.sort(),
                    query.page(),
                    AuctionListPagePolicy.PAGE_SIZE
            );
            if (redisPage.isPresent()) {
                metrics.recordLookup("redis", "hit");
                return CompletableFuture.completedFuture(resolved(
                        redisPage.get(),
                        serverTime,
                        query.page(),
                        null
                ));
            }
            metrics.recordLookup("redis", "miss");
            return resetToLatest(query.sort(), serverTime);
        } catch (RedisSnapshotUnavailableException exception) {
            metrics.recordLookup("redis", "error");
            return build(
                    DownPriceSnapshotBuildKey.exact(generationAt),
                    query.sort(),
                    query.page(),
                    serverTime,
                    null
            );
        }
    }

    private CompletableFuture<ResolvedDownPriceSnapshotPage> resetToLatest(
            AuctionSort sort,
            LocalDateTime serverTime
    ) {
        metrics.recordReset("expired");
        return resolveLatest(
                sort,
                1,
                serverTime,
                SnapshotResetReason.GENERATION_EXPIRED
        );
    }

    private CompletableFuture<ResolvedDownPriceSnapshotPage> resolveLatest(
            AuctionSort sort,
            int page,
            LocalDateTime serverTime,
            SnapshotResetReason resetReason
    ) {
        try {
            Optional<DownPriceSnapshotPage> redisPage = redisStore.findLatestPage(
                    sort,
                    page,
                    AuctionListPagePolicy.PAGE_SIZE
            );
            if (redisPage.isPresent()) {
                metrics.recordLookup("redis", "hit");
                return CompletableFuture.completedFuture(resolved(
                        redisPage.get(),
                        serverTime,
                        page,
                        resetReason
                ));
            }
            metrics.recordLookup("redis", "miss");
        } catch (RedisSnapshotUnavailableException exception) {
            metrics.recordLookup("redis", "error");
        }

        return build(
                DownPriceSnapshotBuildKey.latestSlot(serverTime),
                sort,
                page,
                serverTime,
                resetReason
        );
    }

    private CompletableFuture<ResolvedDownPriceSnapshotPage> build(
            DownPriceSnapshotBuildKey key,
            AuctionSort sort,
            int page,
            LocalDateTime serverTime,
            SnapshotResetReason resetReason
    ) {
        return buildCoordinator.getOrBuild(key)
                .thenApply(snapshot -> {
                    metrics.recordLookup("db", "hit");
                    return resolved(
                            slice(snapshot, sort, page),
                            serverTime,
                            page,
                            resetReason
                    );
                });
    }

    private ResolvedDownPriceSnapshotPage resolved(
            DownPriceSnapshotPage page,
            LocalDateTime serverTime,
            int effectivePage,
            SnapshotResetReason resetReason
    ) {
        metrics.recordGenerationAge(Duration.between(page.generationAt(), serverTime));
        return new ResolvedDownPriceSnapshotPage(
                page,
                serverTime,
                effectivePage,
                resetReason
        );
    }

    private DownPriceSnapshotPage slice(
            DownPriceSnapshot snapshot,
            AuctionSort sort,
            int page
    ) {
        List<AuctionPriceSnapshot> entries = snapshot.entries(sort);
        int fromIndex = Math.min(
                (page - 1) * AuctionListPagePolicy.PAGE_SIZE,
                entries.size()
        );
        int toIndex = Math.min(
                fromIndex + AuctionListPagePolicy.PAGE_SIZE,
                entries.size()
        );
        return new DownPriceSnapshotPage(
                snapshot.generationAt(),
                entries.subList(fromIndex, toIndex),
                entries.size()
        );
    }
}
