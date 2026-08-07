package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuctionClosingService {

    private final AuctionRepository auctionRepository;
    private final UpAuctionSettlementService upAuctionSettlementService;

    @Transactional
    public boolean closeIfAvailable(long auctionId) {
        if (auctionRepository.findClosingCandidateIdForUpdateSkipLocked(auctionId)
                .isEmpty()) {
            return false;
        }

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new IllegalStateException(
                        "선점한 마감 대상 경매를 찾을 수 없습니다."
                ));

        if (auction.getStatus() == AuctionStatus.OPEN) {
            auction.markUnsold(auctionRepository.currentDatabaseTime());
            return true;
        }
        if (auction.getStatus() == AuctionStatus.BID_ONGOING) {
            upAuctionSettlementService.settle(auctionId);
            return true;
        }
        return false;
    }
}
