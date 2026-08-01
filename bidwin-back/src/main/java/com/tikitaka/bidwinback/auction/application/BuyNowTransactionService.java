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
        // 요구사항: 완료된 동일 요청은 새 거래를 만들지 않고 기존 결과를 반환한다.
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

        // 요구사항: 동시 구매 시 DB 조건부 갱신에 성공한 한 요청만 낙찰된다.
        int completed = auctionRepository.completeForBuyNow(auctionId, memberId);
        if (completed != 1) {
            throw new BidException(CONCURRENT_TRADE_CONFLICT);
        }

        // 요구사항: 서버 간 시각 차이 없이 DB가 확정한 완료 시각으로 최종가를 계산한다.
        LocalDateTime purchasedAt = auctionRepository.findCompletedAt(auctionId)
                .orElseThrow(() -> new IllegalStateException(
                        "즉시구매 완료 시각을 조회할 수 없습니다."
                ));
        long finalPrice = priceCalculator.calculate(auction, purchasedAt);

        // 요구사항: 낙찰자의 HELD 보증금은 같은 트랜잭션에서 한 번만 USED로 전환한다.
        int usedDeposits = auctionDepositRepository.useHeldDeposit(memberId, auctionId);
        if (usedDeposits != 1) {
            throw new BidException(INSUFFICIENT_DEPOSIT);
        }

        // 요구사항: 거래와 멱등 요청 로그를 함께 저장해 재요청 결과를 동일하게 보장한다.
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
        // 요구사항: 하나의 멱등 키를 다른 회원이나 경매 요청에 재사용할 수 없다.
        if (!requestLog.matches(memberId, auctionId)) {
            throw new BidException(IDEMPOTENCY_KEY_REUSED);
        }
    }

    private void validateBuyer(Member buyer, Auction auction) {
        // 요구사항: ACTIVE 회원만 즉시구매할 수 있다.
        if (buyer.getStatus() != MemberStatus.ACTIVE) {
            throw new MemberException(MEMBER_NOT_ACTIVE);
        }
        // 요구사항: 판매자는 자신의 경매를 구매할 수 없다.
        if (auction.getSeller().getId().equals(buyer.getId())) {
            throw new BidException(SELF_PURCHASE_NOT_ALLOWED);
        }
    }

    private void validateAuction(Auction auction) {
        // 요구사항: 이미 거래가 확정된 경매는 다시 구매할 수 없다.
        if (auction.getStatus() == AuctionStatus.COMPLETED) {
            throw new AuctionException(AUCTION_ALREADY_TRADED);
        }
        // 요구사항: 첫 입찰 전 OPEN 상태의 경매만 즉시구매할 수 있다.
        if (auction.getStatus() != AuctionStatus.OPEN) {
            throw new AuctionException(AUCTION_NOT_ONGOING);
        }
        // 요구사항: 상향 경매는 판매자가 즉시구매가를 설정한 경우에만 구매할 수 있다.
        if (auction instanceof UpAuction upAuction
                && upAuction.getBuyNowPrice() == null) {
            throw new BidException(BUY_NOW_PRICE_NOT_SET);
        }

        // 요구사항: DB 현재 시각이 종료 시각과 같거나 지난 경매는 구매할 수 없다.
        LocalDateTime databaseTime = auctionRepository.currentDatabaseTime();
        if (!auction.getEndedAt().isAfter(databaseTime)) {
            throw new AuctionException(AUCTION_ALREADY_ENDED);
        }
    }

    private void validateDeposit(Long memberId, Long auctionId) {
        // 요구사항: 0원보다 큰 HELD 보증금이 있어야 즉시구매할 수 있다.
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
