package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.application.live.AuctionBidHistoryRevealed;
import com.tikitaka.bidwinback.auction.application.live.AuctionStateChanged;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionTradeRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionClosingCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuctionClosingService {

    private final AuctionRepository auctionRepository;
    private final AuctionTradeRepository auctionTradeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public int closeBatch(AuctionStatus candidateStatus, int batchSize) {
        List<AuctionClosingCandidate> candidates =
                auctionRepository.findClosingCandidatesForUpdateSkipLocked(
                        candidateStatus.name(),
                        batchSize
                );
        if (candidates.isEmpty()) {
            return 0;
        }

        LocalDateTime settledAt = auctionRepository.currentDatabaseTime();
        List<Long> claimedAuctionIds = candidates.stream()
                .map(AuctionClosingCandidate::getAuctionId)
                .toList();

        auctionTradeRepository.insertWinnerTradesAll(
                claimedAuctionIds,
                TradeStatus.WAITING_CONFIRM.name(),
                settledAt
        );
        int closed = auctionRepository.completeAll(claimedAuctionIds, settledAt);
        closed += auctionRepository.markUnsoldAll(claimedAuctionIds, settledAt);
        verifyAllClosed(closed, candidates.size());

        publishClosedEvent(candidates, candidateStatus);
        return candidates.size();
    }

    private void verifyAllClosed(int closed, int claimed) {
        if (closed != claimed) {
            throw new IllegalStateException(
                    "경매 마감 배치의 반영 수가 선점 수와 다릅니다. claimed=" + claimed
                            + ", closed=" + closed
            );
        }
    }

    private void publishClosedEvent(
            List<AuctionClosingCandidate> candidates,
            AuctionStatus candidateStatus
    ) {
        for (AuctionClosingCandidate candidate : candidates) {
            eventPublisher.publishEvent(new AuctionStateChanged(candidate.getAuctionId()));
            // 공개입찰조차 없던 경매(OPEN)는 공개할 밀봉 입찰 내역이 없다.
            if (candidateStatus == AuctionStatus.BID_ONGOING) {
                eventPublisher.publishEvent(new AuctionBidHistoryRevealed(
                        candidate.getAuctionId(),
                        candidate.getRevision() + 1
                ));
            }
        }
    }
}
