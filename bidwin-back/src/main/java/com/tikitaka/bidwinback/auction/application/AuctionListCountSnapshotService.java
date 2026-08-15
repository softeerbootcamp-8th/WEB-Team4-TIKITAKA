package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionListQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListSearchCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuctionListCountSnapshotService {

    private final AuctionRepository auctionRepository;
    private final AuctionListQueryRepository auctionListQueryRepository;

    @Transactional(readOnly = true)
    public AuctionListCounts capture() {
        LocalDateTime databaseTime = auctionRepository.currentDatabaseTime();
        long upCount = auctionListQueryRepository.count(condition(AuctionType.UP, databaseTime));
        long downCount = auctionListQueryRepository.count(condition(AuctionType.DOWN, databaseTime));
        long allCount = Math.addExact(upCount, downCount);
        return new AuctionListCounts(allCount, upCount, downCount);
    }

    private AuctionListSearchCondition condition(
            AuctionType auctionType,
            LocalDateTime databaseTime
    ) {
        return new AuctionListSearchCondition(
                auctionType,
                AuctionSort.RECOMMENDED,
                null,
                databaseTime
        );
    }
}
