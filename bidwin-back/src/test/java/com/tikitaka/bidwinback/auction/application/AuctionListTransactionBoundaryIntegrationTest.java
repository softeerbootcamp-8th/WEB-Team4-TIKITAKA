package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionListQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionListResponse;
import com.tikitaka.bidwinback.global.storage.ImageUrlResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
        classes = AuctionListTransactionBoundaryIntegrationTest.TestConfig.class
)
class AuctionListTransactionBoundaryIntegrationTest {

    private static final LocalDateTime SNAPSHOT_AT =
            LocalDateTime.of(2026, 8, 15, 12, 0);
    private static final LocalDateTime SERVER_TIME = SNAPSHOT_AT.plusSeconds(30);

    @Autowired
    private AuctionListService auctionListService;
    @Autowired
    private AuctionListDbQuery auctionListDbQuery;
    @Autowired
    private AuctionRepository auctionRepository;
    @Autowired
    private AuctionListQueryRepository auctionListQueryRepository;
    @Autowired
    private DownPriceSnapshotCache downPriceSnapshotCache;

    @BeforeEach
    void setUp() {
        reset(
                auctionRepository,
                auctionListQueryRepository,
                downPriceSnapshotCache
        );
    }

    @Test
    void Redis_조회는_트랜잭션_밖에서_실행하고_hit_DB_조립만_readOnly_트랜잭션으로_감싼다() {
        DownPriceSnapshotCache.Metadata metadata =
                new DownPriceSnapshotCache.Metadata(SNAPSHOT_AT, 0L);
        AtomicBoolean latestTransactionActive = new AtomicBoolean(true);
        AtomicBoolean pageTransactionActive = new AtomicBoolean(true);
        AtomicBoolean dbTransactionActive = new AtomicBoolean();
        AtomicBoolean dbTransactionReadOnly = new AtomicBoolean();
        when(downPriceSnapshotCache.findLatest()).thenAnswer(invocation -> {
            latestTransactionActive.set(
                    TransactionSynchronizationManager.isActualTransactionActive()
            );
            return Optional.of(metadata);
        });
        when(downPriceSnapshotCache.findPage(
                metadata,
                AuctionSort.PRICE_LOW,
                0L,
                16
        )).thenAnswer(invocation -> {
            pageTransactionActive.set(
                    TransactionSynchronizationManager.isActualTransactionActive()
            );
            return Optional.of(List.of());
        });
        when(auctionRepository.currentDatabaseTime()).thenAnswer(invocation -> {
            dbTransactionActive.set(
                    TransactionSynchronizationManager.isActualTransactionActive()
            );
            dbTransactionReadOnly.set(
                    TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            );
            return SERVER_TIME;
        });
        when(auctionListQueryRepository.findDownRowsByPriceSnapshots(
                List.of(),
                SNAPSHOT_AT
        )).thenReturn(List.of());

        AuctionListResponse response = auctionListService.getList(new AuctionListQuery(
                AuctionType.DOWN,
                AuctionSort.PRICE_LOW,
                null,
                null,
                List.of(),
                1,
                16,
                null
        ));

        assertThat(AopUtils.isAopProxy(auctionListService)).isFalse();
        assertThat(AopUtils.isAopProxy(auctionListDbQuery)).isTrue();
        assertThat(latestTransactionActive).isFalse();
        assertThat(pageTransactionActive).isFalse();
        assertThat(dbTransactionActive).isTrue();
        assertThat(dbTransactionReadOnly).isTrue();
        assertThat(response.serverTime()).isNotZero();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void 폴백_DB_조회_전체를_하나의_readOnly_트랜잭션으로_감싼다() {
        AtomicBoolean timeTransactionActive = new AtomicBoolean();
        AtomicBoolean countTransactionActive = new AtomicBoolean();
        AtomicBoolean countTransactionReadOnly = new AtomicBoolean();
        when(auctionRepository.currentDatabaseTime()).thenAnswer(invocation -> {
            timeTransactionActive.set(
                    TransactionSynchronizationManager.isActualTransactionActive()
            );
            return SERVER_TIME;
        });
        when(auctionListQueryRepository.count(any())).thenAnswer(invocation -> {
            countTransactionActive.set(
                    TransactionSynchronizationManager.isActualTransactionActive()
            );
            countTransactionReadOnly.set(
                    TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            );
            return 0L;
        });

        auctionListService.getList(new AuctionListQuery(
                AuctionType.UP,
                AuctionSort.LATEST,
                null,
                null,
                List.of(),
                1,
                16,
                null
        ));

        assertThat(timeTransactionActive).isTrue();
        assertThat(countTransactionActive).isTrue();
        assertThat(countTransactionReadOnly).isTrue();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new TestTransactionManager();
        }

        @Bean
        AuctionRepository auctionRepository() {
            return mock(AuctionRepository.class);
        }

        @Bean
        AuctionListQueryRepository auctionListQueryRepository() {
            return mock(AuctionListQueryRepository.class);
        }

        @Bean
        AuctionPricePageQuery auctionPricePageQuery() {
            return mock(AuctionPricePageQuery.class);
        }

        @Bean
        DownPriceSnapshotCache downPriceSnapshotCache() {
            return mock(DownPriceSnapshotCache.class);
        }

        @Bean
        ImageUrlResolver imageUrlResolver() {
            return mock(ImageUrlResolver.class);
        }

        @Bean
        AuctionListDbQuery auctionListDbQuery(
                AuctionRepository auctionRepository,
                AuctionListQueryRepository auctionListQueryRepository,
                AuctionPricePageQuery auctionPricePageQuery
        ) {
            return new AuctionListDbQuery(
                    auctionRepository,
                    auctionListQueryRepository,
                    auctionPricePageQuery
            );
        }

        @Bean
        AuctionListService auctionListService(
                AuctionListDbQuery auctionListDbQuery,
                DownPriceSnapshotCache downPriceSnapshotCache,
                ImageUrlResolver imageUrlResolver
        ) {
            return new AuctionListService(
                    auctionListDbQuery,
                    downPriceSnapshotCache,
                    imageUrlResolver
            );
        }
    }

    private static final class TestTransactionManager
            extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(
                Object transaction,
                TransactionDefinition definition
        ) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
