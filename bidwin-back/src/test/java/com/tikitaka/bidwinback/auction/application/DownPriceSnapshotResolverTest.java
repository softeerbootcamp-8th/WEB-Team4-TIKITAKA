package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionListStatusFilter;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import com.tikitaka.bidwinback.auction.infrastructure.RedisSnapshotStore;
import com.tikitaka.bidwinback.auction.infrastructure.RedisSnapshotUnavailableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DownPriceSnapshotResolverTest {

    private static final LocalDateTime SERVER_TIME =
            LocalDateTime.of(2026, 8, 18, 12, 10);

    @Mock
    private RedisSnapshotStore redisStore;

    @Mock
    private DownPriceSnapshotBuildCoordinator buildCoordinator;

    @Mock
    private AuctionDatabaseTimeQuery databaseTimeQuery;

    private DownPriceSnapshotResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DownPriceSnapshotResolver(
                redisStore,
                buildCoordinator,
                databaseTimeQuery,
                new DownPriceSnapshotMetrics(new SimpleMeterRegistry()),
                Duration.ofMinutes(10)
        );
    }

    @Test
    void 지정된_DOWN_ACTIVE_가격순_16개_1에서100페이지만_지원한다() {
        assertThat(resolver.supports(query(AuctionSort.PRICE_LOW, 1, null))).isTrue();
        assertThat(resolver.supports(query(AuctionSort.PRICE_HIGH, 100, null))).isTrue();
        assertThat(resolver.supports(new AuctionListQuery(
                AuctionType.DOWN,
                AuctionSort.PRICE_LOW,
                null,
                AuctionListStatusFilter.ENDED,
                null,
                1,
                16,
                null
        ))).isFalse();
        assertThat(resolver.supports(new AuctionListQuery(
                AuctionType.DOWN,
                AuctionSort.PRICE_LOW,
                "검색",
                AuctionListStatusFilter.ACTIVE,
                null,
                1,
                16,
                null
        ))).isFalse();
        assertThat(resolver.supports(new AuctionListQuery(
                AuctionType.DOWN,
                AuctionSort.PRICE_LOW,
                null,
                AuctionListStatusFilter.ACTIVE,
                AuctionCategory.FOOD,
                1,
                16,
                null
        ))).isFalse();
        assertThat(resolver.supports(new AuctionListQuery(
                AuctionType.DOWN,
                AuctionSort.PRICE_LOW,
                null,
                AuctionListStatusFilter.ACTIVE,
                null,
                1,
                15,
                null
        ))).isFalse();
        assertThat(resolver.supports(query(AuctionSort.PRICE_LOW, 101, null))).isFalse();
    }

    @Test
    void 첫_요청은_Redis_최신_세대의_해당_페이지만_반환한다() {
        LocalDateTime generationAt = SERVER_TIME.minusSeconds(10);
        SnapshotGenerationPage page = page(generationAt, 20, 17L);
        AuctionListQuery query = query(AuctionSort.PRICE_LOW, 2, null);
        when(databaseTimeQuery.currentTime()).thenReturn(SERVER_TIME);
        when(redisStore.findLatestPage(AuctionSort.PRICE_LOW, 2, 16))
                .thenReturn(Optional.of(page));

        ResolvedSnapshot resolved = resolver.resolve(query).join();

        assertThat(resolved.snapshot()).isSameAs(page);
        assertThat(resolved.effectivePage()).isEqualTo(2);
        assertThat(resolved.reset()).isFalse();
        verify(buildCoordinator, never()).getOrBuild(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void exact_세대는_Redis에서_조회한다() {
        LocalDateTime generationAt = SERVER_TIME.minusMinutes(1);
        SnapshotGenerationPage page = page(generationAt, 1, 1L);
        AuctionListQuery query = query(AuctionSort.PRICE_LOW, 1, generationAt);
        when(databaseTimeQuery.currentTime()).thenReturn(SERVER_TIME);
        when(redisStore.findExactPage(generationAt, AuctionSort.PRICE_LOW, 1, 16))
                .thenReturn(Optional.of(page));

        ResolvedSnapshot resolved = resolver.resolve(query).join();

        assertThat(resolved.snapshot()).isSameAs(page);
        verify(buildCoordinator, never()).getOrBuild(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void Redis가_정상인데_exact_세대가_없으면_최신_1페이지로_reset한다() {
        LocalDateTime expiredGeneration = SERVER_TIME.minusMinutes(1);
        LocalDateTime latestGeneration = SERVER_TIME.minusSeconds(10);
        AuctionListQuery query = query(AuctionSort.PRICE_HIGH, 5, expiredGeneration);
        SnapshotGenerationPage latest = page(latestGeneration, 30, 30L);
        when(databaseTimeQuery.currentTime()).thenReturn(SERVER_TIME);
        when(redisStore.findExactPage(expiredGeneration, AuctionSort.PRICE_HIGH, 5, 16))
                .thenReturn(Optional.empty());
        when(redisStore.findLatestPage(AuctionSort.PRICE_HIGH, 1, 16))
                .thenReturn(Optional.of(latest));

        ResolvedSnapshot resolved = resolver.resolve(query).join();

        assertThat(resolved.snapshot()).isSameAs(latest);
        assertThat(resolved.effectivePage()).isEqualTo(1);
        assertThat(resolved.reset()).isTrue();
        assertThat(resolved.resetReason()).isEqualTo(SnapshotResetReason.GENERATION_EXPIRED);
    }

    @Test
    void Redis_장애중_exact_세대는_같은_asOf로_DB에서_재생성한다() {
        LocalDateTime generationAt = SERVER_TIME.minusMinutes(1);
        AuctionListQuery query = query(AuctionSort.PRICE_LOW, 1, generationAt);
        SnapshotBuildKey key = SnapshotBuildKey.exact(generationAt);
        DownPriceSnapshot built = snapshot(generationAt, 1L);
        when(databaseTimeQuery.currentTime()).thenReturn(SERVER_TIME);
        when(redisStore.findExactPage(generationAt, AuctionSort.PRICE_LOW, 1, 16))
                .thenThrow(new RedisSnapshotUnavailableException("Redis 장애"));
        when(buildCoordinator.getOrBuild(key))
                .thenReturn(CompletableFuture.completedFuture(built));

        ResolvedSnapshot resolved = resolver.resolve(query).join();

        assertThat(resolved.snapshot().generationAt()).isEqualTo(generationAt);
        assertThat(resolved.reset()).isFalse();
        verify(buildCoordinator).getOrBuild(key);
    }

    @Test
    void Redis_장애중_최신_세대는_DB에서_생성한다() {
        AuctionListQuery query = query(AuctionSort.PRICE_LOW, 2, null);
        SnapshotBuildKey key = SnapshotBuildKey.latestSlot(SERVER_TIME);
        DownPriceSnapshot built = snapshot(key.generationAt(), 1L);
        when(databaseTimeQuery.currentTime()).thenReturn(SERVER_TIME);
        when(redisStore.findLatestPage(AuctionSort.PRICE_LOW, 2, 16))
                .thenThrow(new RedisSnapshotUnavailableException("Redis 장애"));
        when(buildCoordinator.getOrBuild(key))
                .thenReturn(CompletableFuture.completedFuture(built));

        ResolvedSnapshot resolved = resolver.resolve(query).join();

        assertThat(resolved.snapshot().generationAt()).isEqualTo(key.generationAt());
        assertThat(resolved.effectivePage()).isEqualTo(2);
        verify(buildCoordinator).getOrBuild(key);
    }

    @Test
    void 보존기간_10분을_초과한_세대는_저장소를_조회하지_않고_최신_1페이지로_reset한다() {
        LocalDateTime expiredGeneration = SERVER_TIME.minusMinutes(10).minusNanos(1);
        AuctionListQuery query = query(AuctionSort.PRICE_LOW, 3, expiredGeneration);
        SnapshotGenerationPage latest = page(SERVER_TIME.minusSeconds(5), 1, 1L);
        when(databaseTimeQuery.currentTime()).thenReturn(SERVER_TIME);
        when(redisStore.findLatestPage(AuctionSort.PRICE_LOW, 1, 16))
                .thenReturn(Optional.of(latest));

        ResolvedSnapshot resolved = resolver.resolve(query).join();

        assertThat(resolved.reset()).isTrue();
        assertThat(resolved.effectivePage()).isEqualTo(1);
        verify(redisStore, never()).findExactPage(
                expiredGeneration,
                AuctionSort.PRICE_LOW,
                3,
                16
        );
    }

    @Test
    void 서버가_발급하지_않은_세대_시각은_DB로_재생성하지_않고_reset한다() {
        LocalDateTime invalidGeneration = SERVER_TIME.minusSeconds(15);
        AuctionListQuery query = query(AuctionSort.PRICE_LOW, 3, invalidGeneration);
        SnapshotGenerationPage latest = page(SERVER_TIME.minusSeconds(30), 1, 1L);
        when(databaseTimeQuery.currentTime()).thenReturn(SERVER_TIME);
        when(redisStore.findLatestPage(AuctionSort.PRICE_LOW, 1, 16))
                .thenReturn(Optional.of(latest));

        ResolvedSnapshot resolved = resolver.resolve(query).join();

        assertThat(resolved.reset()).isTrue();
        assertThat(resolved.effectivePage()).isEqualTo(1);
        verify(redisStore, never()).findExactPage(
                invalidGeneration,
                AuctionSort.PRICE_LOW,
                3,
                16
        );
        verify(buildCoordinator, never()).getOrBuild(SnapshotBuildKey.exact(invalidGeneration));
    }

    private AuctionListQuery query(
            AuctionSort sort,
            int page,
            LocalDateTime asOf
    ) {
        return new AuctionListQuery(
                AuctionType.DOWN,
                sort,
                null,
                AuctionListStatusFilter.ACTIVE,
                null,
                page,
                16,
                asOf
        );
    }

    private SnapshotGenerationPage page(
            LocalDateTime generationAt,
            int totalCount,
            long auctionId
    ) {
        return new SnapshotGenerationPage(
                generationAt,
                totalCount,
                List.of(new AuctionPriceSnapshot(auctionId, 100L, 90L))
        );
    }

    private DownPriceSnapshot snapshot(LocalDateTime generationAt, long auctionId) {
        List<AuctionPriceSnapshot> entries = List.of(
                new AuctionPriceSnapshot(auctionId, 100L, 90L)
        );
        return new DownPriceSnapshot(generationAt, entries, entries);
    }
}
