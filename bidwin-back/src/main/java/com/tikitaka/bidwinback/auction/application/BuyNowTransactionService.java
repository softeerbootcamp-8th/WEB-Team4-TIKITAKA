package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.entity.BuyNowRequestLog;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.DepositStatus;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.domain.exception.BidException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionDepositRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionTradeRepository;
import com.tikitaka.bidwinback.auction.domain.repository.BuyNowIdempotencyStore;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import com.tikitaka.bidwinback.member.domain.exception.MemberException;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_ALREADY_ENDED;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_ALREADY_TRADED;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_NOT_FOUND;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_NOT_ONGOING;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.BUY_NOW_PRICE_NOT_SET;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.CONCURRENT_TRADE_CONFLICT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.IDEMPOTENCY_KEY_REUSED;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INSUFFICIENT_DEPOSIT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.MEMBER_NOT_ACTIVE;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.MEMBER_NOT_FOUND;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.SELF_PURCHASE_NOT_ALLOWED;

@Service
@RequiredArgsConstructor
public class BuyNowTransactionService {

    private final MemberRepository memberRepository;
    private final AuctionRepository auctionRepository;
    private final AuctionDepositRepository auctionDepositRepository;
    private final AuctionTradeRepository auctionTradeRepository;
    private final BuyNowIdempotencyStore idempotencyStore;
    private final BuyNowPriceCalculator priceCalculator;

    @Transactional
    public BuyNowResult buy(
            Long memberId,
            Long auctionId,
            String idempotencyKey
    ) {
        Optional<BuyNowResult> replay = replayIfPresent(
                memberId,
                auctionId,
                idempotencyKey
        );
        if (replay.isPresent()) {
            return replay.get();
        }

        Member buyer = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MEMBER_NOT_FOUND));
        Auction auction = auctionRepository.findWithSellerById(auctionId)
                .orElseThrow(() -> new AuctionException(AUCTION_NOT_FOUND));

        validateBuyer(buyer, auction);
        validateAuction(auction);
        validateDeposit(memberId, auctionId);

        int completed = auctionRepository.completeForBuyNow(auctionId, memberId);
        if (completed != 1) {
            throw new BidException(CONCURRENT_TRADE_CONFLICT);
        }

        LocalDateTime purchasedAt = auctionRepository.findCompletedAt(auctionId)
                .orElseThrow(() -> new IllegalStateException(
                        "즉시구매 완료 시각을 조회할 수 없습니다."
                ));
        long finalPrice = priceCalculator.calculate(auction, purchasedAt);

        int usedDeposits = auctionDepositRepository.useHeldDeposit(memberId, auctionId);
        if (usedDeposits != 1) {
            throw new BidException(INSUFFICIENT_DEPOSIT);
        }

        AuctionTrade trade = auctionTradeRepository.saveAndFlush(
                AuctionTrade.builder()
                        .auction(auction)
                        .buyer(buyer)
                        .finalPrice(finalPrice)
                        .purchasedAt(purchasedAt)
                        .build()
        );
        idempotencyStore.saveAndFlush(BuyNowRequestLog.completed(
                idempotencyKey,
                buyer,
                auction,
                trade
        ));

        return BuyNowResult.from(trade);
    }

    @Transactional(readOnly = true)
    public Optional<BuyNowResult> replay(
            Long memberId,
            Long auctionId,
            String idempotencyKey
    ) {
        return replayIfPresent(memberId, auctionId, idempotencyKey);
    }

    private Optional<BuyNowResult> replayIfPresent(
            Long memberId,
            Long auctionId,
            String idempotencyKey
    ) {
        return idempotencyStore.findByKey(idempotencyKey)
                .map(requestLog -> {
                    validateIdempotencyKey(requestLog, memberId, auctionId);
                    AuctionTrade trade = requestLog.getTrade();
                    if (trade == null) {
                        throw new IllegalStateException(
                                "완료되지 않은 즉시구매 요청 로그입니다."
                        );
                    }
                    return BuyNowResult.from(trade);
                });
    }

    private void validateIdempotencyKey(
            BuyNowRequestLog requestLog,
            Long memberId,
            Long auctionId
    ) {
        if (!requestLog.matches(memberId, auctionId)) {
            throw new BidException(IDEMPOTENCY_KEY_REUSED);
        }
    }

    private void validateBuyer(Member buyer, Auction auction) {
        if (buyer.getStatus() != MemberStatus.ACTIVE) {
            throw new MemberException(MEMBER_NOT_ACTIVE);
        }
        if (auction.getSeller().getId().equals(buyer.getId())) {
            throw new BidException(SELF_PURCHASE_NOT_ALLOWED);
        }
    }

    private void validateAuction(Auction auction) {
        if (auction.getStatus() == AuctionStatus.COMPLETED) {
            throw new AuctionException(AUCTION_ALREADY_TRADED);
        }
        if (auction.getStatus() != AuctionStatus.OPEN) {
            throw new AuctionException(AUCTION_NOT_ONGOING);
        }
        if (auction instanceof UpAuction upAuction
                && upAuction.getBuyNowPrice() == null) {
            throw new BidException(BUY_NOW_PRICE_NOT_SET);
        }

        LocalDateTime databaseTime = auctionRepository.currentDatabaseTime();
        if (!auction.getEndedAt().isAfter(databaseTime)) {
            throw new AuctionException(AUCTION_ALREADY_ENDED);
        }
    }

    private void validateDeposit(Long memberId, Long auctionId) {
        boolean hasDeposit = auctionDepositRepository
                .existsByMemberIdAndAuctionIdAndStatusAndReservedAmountGreaterThan(
                        memberId,
                        auctionId,
                        DepositStatus.HELD,
                        0
                );
        if (!hasDeposit) {
            throw new BidException(INSUFFICIENT_DEPOSIT);
        }
    }
}
