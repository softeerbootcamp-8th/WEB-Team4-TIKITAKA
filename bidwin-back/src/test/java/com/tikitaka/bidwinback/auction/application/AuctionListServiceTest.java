package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionListResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionListServiceTest {

    @Mock
    private DownPriceSnapshotResolver snapshotResolver;

    @Mock
    private AuctionListDbQuery dbQuery;

    @Mock
    private DownPriceSnapshotPageAssembler pageAssembler;

    private final Executor directExecutor = Runnable::run;

    @Test
    void 캐시_대상이_아니면_기존_DB_조회를_그대로_사용한다() {
        AuctionListQuery query = query(AuctionType.UP, AuctionSort.PRICE_LOW);
        AuctionListResponse response = response();
        when(snapshotResolver.supports(query)).thenReturn(false);
        when(dbQuery.findPage(query)).thenReturn(response);

        CompletableFuture<AuctionListResponse> result = service().getList(query);

        assertThat(result).isCompletedWithValue(response);
        verify(snapshotResolver, never()).resolve(query);
    }

    @Test
    void 캐시_대상이면_Resolver_완료후_별도_Executor에서_페이지를_조립한다() {
        AuctionListQuery query = query(AuctionType.DOWN, AuctionSort.PRICE_LOW);
        ResolvedDownPriceSnapshotPage resolved = new ResolvedDownPriceSnapshotPage(
                new DownPriceSnapshotPage(
                        java.time.LocalDateTime.of(2026, 8, 18, 12, 0),
                        List.of()
                ),
                java.time.LocalDateTime.of(2026, 8, 18, 12, 0, 1),
                1,
                null
        );
        AuctionListResponse response = response();
        when(snapshotResolver.supports(query)).thenReturn(true);
        when(snapshotResolver.resolve(query)).thenReturn(CompletableFuture.completedFuture(resolved));
        when(pageAssembler.assemble(resolved)).thenReturn(response);

        CompletableFuture<AuctionListResponse> result = service().getList(query);

        assertThat(result).isCompletedWithValue(response);
        verify(dbQuery, never()).findPage(query);
    }

    private AuctionListService service() {
        return new AuctionListService(
                snapshotResolver,
                dbQuery,
                pageAssembler,
                directExecutor
        );
    }

    private AuctionListQuery query(AuctionType type, AuctionSort sort) {
        return new AuctionListQuery(type, sort, null, null, null, 1, 16, null);
    }

    private AuctionListResponse response() {
        return new AuctionListResponse(List.of(), 1L, 1L, 1, 1, 0L);
    }
}
