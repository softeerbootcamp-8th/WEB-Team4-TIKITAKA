package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionListResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class AuctionListService {

    static final int MAX_LIST_PAGES = 100;
    static final int DEFAULT_PAGE_SIZE = 16;

    private final DownPriceSnapshotResolver snapshotResolver;
    private final AuctionListDbQuery dbQuery;
    private final DownPriceSnapshotPageAssembler pageAssembler;
    private final Executor pageAssemblyExecutor;

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
                        pageAssembler::assemble,
                        pageAssemblyExecutor
                );
    }
}
