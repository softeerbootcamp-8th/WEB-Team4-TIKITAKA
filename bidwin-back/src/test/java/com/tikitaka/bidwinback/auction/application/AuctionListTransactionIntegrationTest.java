package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionListQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.global.storage.ImageUrlResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AuctionListTransactionIntegrationTest.TestConfig.class)
class AuctionListTransactionIntegrationTest {

    private static final LocalDateTime DATABASE_TIME = LocalDateTime.of(2026, 8, 15, 12, 0);

    @Autowired
    private AuctionListService auctionListService;

    @Autowired
    private AuctionListDbQuery auctionListDbQuery;

    @Autowired
    private AuctionListCountCache countCache;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private AuctionListQueryRepository auctionListQueryRepository;

    @Autowired
    private TestTransactionManager transactionManager;

    @Test
    void Redis는_트랜잭션_밖에서_조회하고_DB_count와_목록은_같은_read_only_트랜잭션에서_실행한다() {
        AtomicReference<Object> databaseTimeTransaction = new AtomicReference<>();

        when(countCache.find(AuctionListCountScope.ALL)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return OptionalLong.empty();
        });
        when(auctionRepository.currentDatabaseTime()).thenAnswer(invocation -> {
            assertReadOnlyTransaction();
            Object transaction = transactionManager.currentTransaction();
            assertThat(transaction).isNotNull();
            databaseTimeTransaction.set(transaction);
            return DATABASE_TIME;
        });
        when(auctionListQueryRepository.count(any())).thenAnswer(invocation -> {
            assertReadOnlyTransaction();
            assertThat(transactionManager.currentTransaction())
                    .isSameAs(databaseTimeTransaction.get());
            return 1L;
        });
        when(auctionListQueryRepository.findPage(any(), anyLong(), anyInt()))
                .thenAnswer(invocation -> {
                    assertReadOnlyTransaction();
                    assertThat(transactionManager.currentTransaction())
                            .isSameAs(databaseTimeTransaction.get());
                    return List.of();
                });

        auctionListService.getList(new AuctionListQuery(
                null,
                AuctionSort.LATEST,
                null,
                null,
                List.of(),
                1,
                16,
                null
        ));

        assertThat(AopUtils.isAopProxy(auctionListDbQuery)).isTrue();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(transactionManager.currentTransaction()).isNull();
    }

    private void assertReadOnlyTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        TestTransactionManager transactionManager() {
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
        AuctionPricePageQuery auctionPricePageQuery(
                AuctionListQueryRepository auctionListQueryRepository
        ) {
            return new AuctionPricePageQuery(auctionListQueryRepository);
        }

        @Bean
        AuctionListCountCache countCache() {
            return mock(AuctionListCountCache.class);
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
                AuctionListCountCache countCache,
                AuctionListDbQuery auctionListDbQuery,
                ImageUrlResolver imageUrlResolver
        ) {
            return new AuctionListService(countCache, auctionListDbQuery, imageUrlResolver);
        }
    }

    static final class TestTransactionManager extends AbstractPlatformTransactionManager {

        private final ThreadLocal<Object> currentTransaction = new ThreadLocal<>();

        Object currentTransaction() {
            return currentTransaction.get();
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            currentTransaction.set(transaction);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            currentTransaction.remove();
        }
    }
}
