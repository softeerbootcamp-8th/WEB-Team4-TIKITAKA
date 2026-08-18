package com.tikitaka.bidwinback.auction.application;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionListTransactionBoundaryTest {

    @Test
    void Redis와_Future를_다루는_서비스와_Resolver는_트랜잭션을_열지_않는다()
            throws NoSuchMethodException {
        assertThat(transactional(AuctionListService.class, "getList", AuctionListQuery.class))
                .isNull();
        assertThat(transactional(
                DownPriceSnapshotResolver.class,
                "resolve",
                AuctionListQuery.class
        )).isNull();
    }

    @Test
    void DB_캡처와_페이지_조립과_fallback만_readOnly_트랜잭션을_연다()
            throws NoSuchMethodException {
        assertReadOnly(SnapshotCaptureService.class, "capture", SnapshotBuildKey.class);
        assertReadOnly(
                SnapshotPageAssembler.class,
                "assemble",
                AuctionListQuery.class,
                ResolvedSnapshot.class
        );
        assertReadOnly(AuctionListDbQuery.class, "findPage", AuctionListQuery.class);
    }

    private void assertReadOnly(
            Class<?> type,
            String methodName,
            Class<?>... parameterTypes
    ) throws NoSuchMethodException {
        Transactional transactional = transactional(type, methodName, parameterTypes);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    private Transactional transactional(
            Class<?> type,
            String methodName,
            Class<?>... parameterTypes
    ) throws NoSuchMethodException {
        Method method = type.getMethod(methodName, parameterTypes);
        return method.getAnnotation(Transactional.class);
    }
}
