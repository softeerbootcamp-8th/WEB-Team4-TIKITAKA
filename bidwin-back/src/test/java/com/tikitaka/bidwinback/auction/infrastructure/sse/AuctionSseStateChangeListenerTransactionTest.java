package com.tikitaka.bidwinback.auction.infrastructure.sse;

import com.tikitaka.bidwinback.auction.application.live.AuctionLiveState;
import com.tikitaka.bidwinback.auction.application.live.AuctionLiveStateService;
import com.tikitaka.bidwinback.auction.application.live.AuctionStateChanged;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.global.sse.SseHub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AuctionSseStateChangeListenerTransactionTest.TestConfig.class)
class AuctionSseStateChangeListenerTransactionTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private AuctionLiveStateService stateService;
    @Autowired
    private SseHub sseHub;

    private final AuctionLiveState state = new AuctionLiveState(
            1L,
            3L,
            AuctionType.UP,
            AuctionStatus.BID_ONGOING,
            130_000L,
            2L
    );

    @BeforeEach
    void setUp() {
        reset(stateService, sseHub);
    }

    @Test
    void 트랜잭션이_커밋된_뒤에만_경매_SSE_채널로_전송한다() {
        // given
        when(sseHub.hasSubscribers(AuctionSseMessages.channel(1L))).thenReturn(true);
        when(stateService.getState(1L)).thenReturn(state);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // when
        transaction.executeWithoutResult(ignored -> {
            eventPublisher.publishEvent(new AuctionStateChanged(1L));
            verifyNoInteractions(stateService, sseHub);
        });

        // then
        verify(sseHub).publish(
                eq(AuctionSseMessages.state(state)),
                anyLong()
        );
    }

    @Test
    void 트랜잭션이_롤백되면_SSE_상태를_조회하거나_전송하지_않는다() {
        // given
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // when
        transaction.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new AuctionStateChanged(1L));
            status.setRollbackOnly();
        });

        // then
        verifyNoInteractions(stateService, sseHub);
    }

    @Test
    void 커밋후_SSE_조회가_실패해도_이미_완료된_비즈니스_결과는_실패하지_않는다() {
        // given
        when(sseHub.hasSubscribers(AuctionSseMessages.channel(1L))).thenReturn(true);
        when(stateService.getState(1L)).thenThrow(new IllegalStateException("snapshot failed"));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // when & then
        assertThatCode(() -> transaction.executeWithoutResult(ignored ->
                eventPublisher.publishEvent(new AuctionStateChanged(1L))
        )).doesNotThrowAnyException();
    }

    @Test
    void 보고_있는_연결이_없으면_상태를_다시_조회하지_않는다() {
        // given
        when(sseHub.hasSubscribers(AuctionSseMessages.channel(1L))).thenReturn(false);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // when
        transaction.executeWithoutResult(ignored ->
                eventPublisher.publishEvent(new AuctionStateChanged(1L))
        );

        // then
        verifyNoInteractions(stateService);
        verify(sseHub, never()).publish(any(), anyLong());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new TestTransactionManager();
        }

        @Bean
        AuctionLiveStateService stateService() {
            return mock(AuctionLiveStateService.class);
        }

        @Bean
        SseHub sseHub() {
            return mock(SseHub.class);
        }

        @Bean
        AuctionSseStateChangeListener auctionSseStateChangeListener(
                AuctionLiveStateService stateService,
                SseHub sseHub
        ) {
            return new AuctionSseStateChangeListener(
                    stateService,
                    sseHub
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
