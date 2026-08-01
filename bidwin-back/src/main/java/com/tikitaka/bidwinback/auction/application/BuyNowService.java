package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.AuctionDeposit;
import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.DepositStatus;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionDepositRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository.InstantPurchaseTarget;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionTradeRepository;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_ALREADY_ENDED;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_NOT_FOUND;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_NOT_ONGOING;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.BUY_NOW_PRICE_NOT_SET;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.CONCURRENT_TRADE_CONFLICT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INSUFFICIENT_DEPOSIT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_INPUT_VALUE;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.SELF_PURCHASE_NOT_ALLOWED;

@Service
@RequiredArgsConstructor
public class BuyNowService {
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;

    private final AuctionRepository auctionRepository;
    private final MemberRepository memberRepository;
    private final AuctionDepositRepository auctionDepositRepository;
    private final AuctionTradeRepository auctionTradeRepository;
    private final InstantPurchaseIdempotencyStore idempotencyStore;

    @Transactional
    public BuyNowResult purchase(
            Long auctionId,
            Long buyerId,
            String idempotencyKey
    ) {
        validateRequest(auctionId, buyerId, idempotencyKey);

        //옥션 가져오기(없으면 예외)
        InstantPurchaseTarget target = auctionRepository
                .findInstantPurchaseTarget(auctionId)
                .orElseThrow(() -> new AuctionException(AUCTION_NOT_FOUND));


        Optional<InstantPurchaseIdempotencyStore.SavedPurchase> savedPurchase =
                idempotencyStore.claim(idempotencyKey, buyerId, auctionId);
        if (savedPurchase.isPresent()) {
            InstantPurchaseIdempotencyStore.SavedPurchase saved = savedPurchase.get();
            return new BuyNowResult(
                    saved.tradeId(),
                    auctionId,
                    buyerId,
                    saved.finalPrice(),
                    true
            );
        }

        validatePurchasable(target, buyerId);
        long finalPrice = calculateFinalPrice(target);
        if (finalPrice <= 0) {
            throw new IllegalStateException("즉시구매 가격은 0보다 커야 합니다.");
        }

        // 가격을 계산한 요청 중 DB의 상태·마감 조건을 먼저 바꾼 단 하나만 구매를 계속한다.
        if (auctionRepository.completeForInstantPurchase(auctionId) != 1) {
            throw new AuctionException(CONCURRENT_TRADE_CONFLICT);
        }

        // 잔액 확인과 전액 잠금을 한 문장으로 처리해 다른 경매와의 동시 구매도 초과 차감하지 않는다.
        if (memberRepository.movePointToLockedIfEnough(buyerId, finalPrice) != 1) {
            throw new AuctionException(INSUFFICIENT_DEPOSIT);
        }

        Auction auction = auctionRepository.getReferenceById(auctionId);
        Member buyer = memberRepository.getReferenceById(buyerId);

        auctionDepositRepository.save(AuctionDeposit.builder()
                .member(buyer)
                .auction(auction)
                .reservedAmount(finalPrice)
                .status(DepositStatus.HELD)
                .build());

        AuctionTrade trade = auctionTradeRepository.saveAndFlush(
                AuctionTrade.builder()
                        .auction(auction)
                        .buyer(buyer)
                        .status(TradeStatus.PAID)
                        .finalPrice(finalPrice)
                        .build()
        );

        idempotencyStore.complete(idempotencyKey, trade, finalPrice);
        return new BuyNowResult(
                trade.getId(),
                auctionId,
                buyerId,
                finalPrice,
                false
        );
    }

    private void validateRequest(
            Long auctionId,
            Long buyerId,
            String idempotencyKey
    ) {
        if (auctionId == null || auctionId <= 0 || buyerId == null || buyerId <= 0) {
            throw new AuctionException(INVALID_INPUT_VALUE);
        }
        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH
                || idempotencyKey.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new AuctionException(INVALID_INPUT_VALUE);
        }
    }

    private void validatePurchasable(
            InstantPurchaseTarget target,
            Long buyerId
    ) {
        AuctionStatus status = AuctionStatus.valueOf(target.getStatus());
        if (status == AuctionStatus.COMPLETED) {
            throw new AuctionException(CONCURRENT_TRADE_CONFLICT);
        }
        if (status != AuctionStatus.OPEN && status != AuctionStatus.BID_ONGOING) {
            throw new AuctionException(AUCTION_NOT_ONGOING);
        }
        if (!target.getEndedAt().isAfter(target.getDatabaseNow())) {
            throw new AuctionException(AUCTION_ALREADY_ENDED);
        }
        if (target.getSellerId().equals(buyerId)) {
            throw new AuctionException(SELF_PURCHASE_NOT_ALLOWED);
        }
    }

    private long calculateFinalPrice(InstantPurchaseTarget target) {
        return switch (target.getAuctionType()) {
            case "UP" -> calculateUpAuctionPrice(target);
            case "DOWN" -> calculateDownAuctionPrice(target);
            default -> throw new IllegalStateException(
                    "지원하지 않는 경매 유형입니다: " + target.getAuctionType()
            );
        };
    }

    private long calculateUpAuctionPrice(InstantPurchaseTarget target) {
        Long buyNowPrice = target.getBuyNowPrice();
        if (buyNowPrice == null) {
            throw new AuctionException(BUY_NOW_PRICE_NOT_SET);
        }
        return buyNowPrice;
    }

    private long calculateDownAuctionPrice(InstantPurchaseTarget target) {
        Long minimumPrice = target.getMinimumPrice();
        Long dropPrice = target.getDropPrice();
        Long dropIntervalMinutes = target.getPriceDropInterval();
        if (minimumPrice == null
                || dropPrice == null
                || dropPrice <= 0
                || dropIntervalMinutes == null
                || dropIntervalMinutes <= 0
                || target.getStartedAt() == null
                || target.getDatabaseNow() == null
                || target.getStartPrice() < minimumPrice) {
            throw new IllegalStateException("하향 경매 가격 조건이 올바르지 않습니다.");
        }

        long elapsedMinutes = Math.max(
                0,
                ChronoUnit.MINUTES.between(
                        target.getStartedAt(),
                        target.getDatabaseNow()
                )
        );
        long elapsedDrops = elapsedMinutes / dropIntervalMinutes;
        long priceRange = target.getStartPrice() - minimumPrice;
        long dropsToFloor = priceRange / dropPrice
                + (priceRange % dropPrice == 0 ? 0 : 1);

        if (elapsedDrops >= dropsToFloor) {
            return minimumPrice;
        }
        return target.getStartPrice() - elapsedDrops * dropPrice;
    }

    public record BuyNowResult(
            Long tradeId,
            Long auctionId,
            Long buyerId,
            long finalPrice,
            boolean replayed
    ) {
    }
}
