package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.repository.AuctionListQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListRow;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionListResponse;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DownPriceSnapshotPageAssembler {

    private final AuctionListQueryRepository auctionListQueryRepository;
    private final AuctionSummaryResponseMapper responseMapper;
    private final DownPriceSnapshotMetrics metrics;

    @Transactional(readOnly = true)
    public AuctionListResponse assemble(ResolvedDownPriceSnapshotPage resolved) {
        Timer.Sample sample = metrics.startPageAssembly();
        try {
            DownPriceSnapshotPage page = resolved.page();
            List<AuctionListRow> rows = auctionListQueryRepository
                    .findDownRowsByPriceSnapshots(page.entries());
            return new AuctionListResponse(
                    rows.stream().map(responseMapper::toSummary).toList(),
                    responseMapper.toEpochMilli(resolved.serverTime()),
                    responseMapper.toEpochMilli(page.generationAt()),
                    resolved.effectivePage(),
                    AuctionListService.MAX_LIST_PAGES,
                    (long) AuctionListService.MAX_LIST_PAGES
                            * AuctionListService.DEFAULT_PAGE_SIZE,
                    resolved.reset(),
                    resolved.resetReason()
            );
        } finally {
            metrics.finishPageAssembly(sample);
        }
    }

}
