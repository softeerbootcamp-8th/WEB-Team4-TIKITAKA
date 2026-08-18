package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionListResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

@Service
public class AuctionListService {

    // CallerRunsPolicy로 요청 스레드에서 실행돼도 DB 페이지 조립은 이 수를 넘지 않는다.
    private static final int PAGE_ASSEMBLY_CONCURRENCY = 2;

    private final DownPriceSnapshotResolver snapshotResolver;
    private final AuctionListDbQuery dbQuery;
    private final DownPriceSnapshotPageAssembler pageAssembler;
    private final Executor pageAssemblyExecutor;
    private final Semaphore pageAssemblyPermits = new Semaphore(
            PAGE_ASSEMBLY_CONCURRENCY,
            true
    );

    public AuctionListService(
            DownPriceSnapshotResolver snapshotResolver,
            AuctionListDbQuery dbQuery,
            DownPriceSnapshotPageAssembler pageAssembler,
            @Qualifier("pageAssemblyExecutor") Executor pageAssemblyExecutor
    ) {
        this.snapshotResolver = snapshotResolver;
        this.dbQuery = dbQuery;
        this.pageAssembler = pageAssembler;
        this.pageAssemblyExecutor = pageAssemblyExecutor;
    }

    public CompletableFuture<AuctionListResponse> getList(AuctionListQuery query) {
        if (!snapshotResolver.supports(query)) {
            return CompletableFuture.completedFuture(dbQuery.findPage(query));
        }

        return snapshotResolver.resolve(query)
                .thenApplyAsync(
                        this::assembleSnapshotPage,
                        pageAssemblyExecutor
                );
    }

    private AuctionListResponse assembleSnapshotPage(ResolvedDownPriceSnapshotPage resolved) {
        try {
            pageAssemblyPermits.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CompletionException(exception);
        }
        try {
            return pageAssembler.assemble(resolved);
        } finally {
            pageAssemblyPermits.release();
        }
    }
}
