package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.application.live.AuctionBidHistoryRevealed;
import com.tikitaka.bidwinback.auction.application.live.AuctionStateChanged;
import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuctionClosingService {

    private final AuctionRepository auctionRepository;
    private final UpAuctionSettlementService upAuctionSettlementService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public boolean closeOneCandidate() {
        Optional<Long> candidateId =
                auctionRepository.findOneClosingCandidateIdForUpdateSkipLocked();
        if (candidateId.isEmpty()) {
            return false;
        }

        long auctionId = candidateId.get();
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new IllegalStateException(
                        "선점한 마감 대상 경매를 찾을 수 없습니다."
                ));

        AuctionStatus initialStatus = auction.getStatus();
        if (initialStatus == AuctionStatus.OPEN) {
            auction.markUnsold(auctionRepository.currentDatabaseTime());
            eventPublisher.publishEvent(new AuctionStateChanged(auctionId));
            return true;
        }
        if (initialStatus == AuctionStatus.BID_ONGOING) {
            upAuctionSettlementService.settle(auctionId);
            eventPublisher.publishEvent(new AuctionBidHistoryRevealed(
                    auction.getId(),
                    auction.getRevision()
            ));
            return true;
        }
        return false;
    }
}
