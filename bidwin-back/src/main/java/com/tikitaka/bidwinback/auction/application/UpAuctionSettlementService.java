package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.entity.Bid;
import com.tikitaka.bidwinback.auction.domain.entity.SealedBid;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.domain.exception.SettlementException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionTradeRepository;
import com.tikitaka.bidwinback.auction.domain.repository.BidRepository;
import com.tikitaka.bidwinback.auction.domain.repository.SealedBidRepository;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_NOT_FOUND;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.NOT_UP_AUCTION;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.SETTLEMENT_NOT_AVAILABLE;

@Service
@RequiredArgsConstructor
public class UpAuctionSettlementService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final SealedBidRepository sealedBidRepository;
    private final AuctionTradeRepository auctionTradeRepository;

    // 종료 감지 스케줄러가 경매 ID 단위로 호출하는 낙찰 처리 진입점이다.
    @Transactional
    public UpAuctionSettlementResult settle(Long auctionId) {
        // 마감 직전에 시작된 입찰의 커밋을 기다리고, 동일 경매의 중복 정산을 직렬화한다.
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new AuctionException(AUCTION_NOT_FOUND));
        if (!(auction instanceof UpAuction)) {
            throw new SettlementException(NOT_UP_AUCTION);
        }

        // 스케줄러 재시도나 중복 실행은 저장된 정산 결과를 그대로 반환한다.
        UpAuctionSettlementResult settled = findSettledResult(auction);
        if (settled != null) {
            return settled;
        }

        LocalDateTime databaseTime = auctionRepository.currentDatabaseTime();
        validateCanSettle(auction, databaseTime);

        // 공개입찰과 별도 저장된 밀봉입찰의 최고가만 읽어 최종 낙찰자를 결정한다.
        Optional<Bid> openWinner = bidRepository.findWinnerByAuctionIdAndStatus(
                auctionId,
                BidStatus.UP
        );
        Optional<SealedBid> sealedWinner = sealedBidRepository.findWinnerByAuctionId(auctionId);

        UpAuctionSettlementResult result = chooseWinner(openWinner, sealedWinner)
                .map(candidate -> complete(auction, candidate, databaseTime))
                .orElseGet(() -> markUnsold(auction, databaseTime));

        return result;
    }

    private UpAuctionSettlementResult findSettledResult(Auction auction) {
        if (auction.getStatus() == AuctionStatus.UNSOLD) {
            return UpAuctionSettlementResult.unsold(
                    auction.getId(),
                    auction.getCompletedAt()
            );
        }
        if (auction.getStatus() != AuctionStatus.COMPLETED) {
            return null;
        }

        AuctionTrade trade = auctionTradeRepository.findByAuctionId(auction.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "완료된 경매의 거래 내역을 찾을 수 없습니다."
                ));
        return UpAuctionSettlementResult.completed(trade);
    }

    private void validateCanSettle(Auction auction, LocalDateTime databaseTime) {
        if (auction.getStatus() != AuctionStatus.OPEN
                && auction.getStatus() != AuctionStatus.BID_ONGOING) {
            throw new SettlementException(SETTLEMENT_NOT_AVAILABLE);
        }
        if (auction.getEndedAt().isAfter(databaseTime)) {
            throw new SettlementException(SETTLEMENT_NOT_AVAILABLE);
        }
    }

    private Optional<WinnerCandidate> chooseWinner(
            Optional<Bid> openWinner,
            Optional<SealedBid> sealedWinner
    ) {
        if (openWinner.isEmpty()) {
            return sealedWinner.map(WinnerCandidate::from);
        }
        if (sealedWinner.isEmpty()) {
            return openWinner.map(WinnerCandidate::from);
        }

        WinnerCandidate open = WinnerCandidate.from(openWinner.orElseThrow());
        WinnerCandidate sealed = WinnerCandidate.from(sealedWinner.orElseThrow());
        // 정상 입력에서 밀봉가는 공개 최고가보다 높다. 레거시 동가는 먼저 확정된 공개 입찰을 우선한다.
        return Optional.of(sealed.price() > open.price() ? sealed : open);
    }

    private UpAuctionSettlementResult complete(
            Auction auction,
            WinnerCandidate winner,
            LocalDateTime settledAt
    ) {
        auction.complete(winner.price(), settledAt);
        AuctionTrade trade = auctionTradeRepository.save(
                AuctionTrade.builder()
                        .auction(auction)
                        .buyer(winner.bidder())
                        .finalPrice(winner.price())
                        .purchasedAt(settledAt)
                        .build()
        );
        return UpAuctionSettlementResult.completed(trade);
    }

    private UpAuctionSettlementResult markUnsold(
            Auction auction,
            LocalDateTime settledAt
    ) {
        auction.markUnsold(settledAt);
        return UpAuctionSettlementResult.unsold(auction.getId(), settledAt);
    }

    private record WinnerCandidate(
            Member bidder,
            long price
    ) {

        private static WinnerCandidate from(Bid bid) {
            return new WinnerCandidate(bid.getBidder(), bid.getPrice());
        }

        private static WinnerCandidate from(SealedBid bid) {
            return new WinnerCandidate(bid.getBidder(), bid.getPrice());
        }
    }
}
