package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.application.live.OpenBidAccepted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BidPriceCacheUpdateListenerTransactionTest.TestConfig.class)
class BidPriceCacheUpdateListenerTransactionTest {

    private static final OpenBidAccepted EVENT = new OpenBidAccepted(
            42L,
            150_000L,
            LocalDateTime.of(2099, 1, 1, 0, 0)
    );

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private BidPriceCache bidPriceCache;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        clearInvocations(bidPriceCache, redisTemplate);
    }

    @Test
    void 커밋전에는_캐시를_갱신하지_않고_커밋직후_정확히_한번_갱신한다() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(ignored -> {
            eventPublisher.publishEvent(EVENT);

            verifyNoInteractions(bidPriceCache);
        });

        verify(bidPriceCache, times(1)).updateCommittedPrice(
                EVENT.auctionId(),
                EVENT.price(),
                EVENT.endedAt()
        );
    }

    @Test
    void 트랜잭션이_롤백되면_캐시를_갱신하지_않는다() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            eventPublisher.publishEvent(EVENT);
            status.setRollbackOnly();
        });

        verifyNoInteractions(bidPriceCache);
    }

    @Test
    void 커밋후_Redis_갱신이_실패해도_이미_완료된_비즈니스_결과는_실패하지_않는다() {
        doThrow(new IllegalStateException("Redis unavailable"))
                .when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatCode(() -> transaction.executeWithoutResult(ignored ->
                eventPublisher.publishEvent(EVENT)
        )).doesNotThrowAnyException();

        verify(bidPriceCache, times(1)).updateCommittedPrice(
                EVENT.auctionId(),
                EVENT.price(),
                EVENT.endedAt()
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new TestTransactionManager();
        }

        @Bean
        StringRedisTemplate redisTemplate() {
            return org.mockito.Mockito.mock(StringRedisTemplate.class);
        }

        @Bean
        BidPriceCache bidPriceCache(StringRedisTemplate redisTemplate) {
            return spy(new BidPriceCache(redisTemplate));
        }

        @Bean
        BidPriceCacheUpdateListener bidPriceCacheUpdateListener(BidPriceCache bidPriceCache) {
            return new BidPriceCacheUpdateListener(bidPriceCache);
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
