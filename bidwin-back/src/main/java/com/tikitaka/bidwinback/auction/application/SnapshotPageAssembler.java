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
public class SnapshotPageAssembler {

    private final AuctionListQueryRepository auctionListQueryRepository;
    private final AuctionSummaryResponseMapper responseMapper;
    private final DownPriceSnapshotMetrics metrics;

    @Transactional(readOnly = true)
    public AuctionListResponse assemble(
            AuctionListQuery query,
            ResolvedSnapshot resolved
    ) {
        Timer.Sample sample = metrics.startPageAssembly();
        try {
            SnapshotGenerationPage snapshot = resolved.snapshot();
            List<AuctionListRow> rows = auctionListQueryRepository
                    .findDownRowsByPriceSnapshots(
                            snapshot.entries(),
                            snapshot.generationAt()
                    );
            return new AuctionListResponse(
                    rows.stream().map(responseMapper::toSummary).toList(),
                    responseMapper.toEpochMilli(resolved.serverTime()),
                    responseMapper.toEpochMilli(snapshot.generationAt()),
                    resolved.effectivePage(),
                    DownPriceSnapshotResolver.MAX_PAGES,
                    (long) DownPriceSnapshotResolver.MAX_PAGES
                            * DownPriceSnapshotResolver.PAGE_SIZE,
                    resolved.reset(),
                    resolved.resetReason()
            );
        } finally {
            metrics.finishPageAssembly(sample);
        }
    }

}
