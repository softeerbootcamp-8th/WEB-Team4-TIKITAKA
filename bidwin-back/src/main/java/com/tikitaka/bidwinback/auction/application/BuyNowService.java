package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.AuctionDeposit;
import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.InstantPurchaseRequest;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.DepositStatus;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionDepositRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionTradeRepository;
import com.tikitaka.bidwinback.auction.domain.repository.InstantPurchaseRequestRepository;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_ALREADY_ENDED;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_NOT_FOUND;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_NOT_ONGOING;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.BUY_NOW_PRICE_NOT_SET;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.CONCURRENT_TRADE_CONFLICT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INSUFFICIENT_DEPOSIT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.IDEMPOTENCY_KEY_REUSED;
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
    private final InstantPurchaseRequestRepository instantPurchaseRequestRepository;

    @Transactional
    public BuyNowResult purchase(
            Long auctionId,
            Long buyerId,
            String idempotencyKey
    ) {
        validateRequest(auctionId, buyerId, idempotencyKey);

        // JOINED 상속 매핑에 맡겨 실제 UpAuction 또는 DownAuction 인스턴스를 복원한다.
        Auction auction = auctionRepository
                .findById(auctionId)
                .orElseThrow(() -> new AuctionException(AUCTION_NOT_FOUND));

        instantPurchaseRequestRepository.insertOrKeep(
                idempotencyKey,
                buyerId,
                auctionId
        );
        InstantPurchaseRequest request = findIdempotencyRequestForUpdate(
                idempotencyKey
        );
        if (!request.belongsTo(buyerId, auctionId)) {
            throw new AuctionException(IDEMPOTENCY_KEY_REUSED);
        }
        if (request.isCompleted()) {
            return new BuyNowResult(
                    request.getTrade().getId(),
                    auctionId,
                    buyerId,
                    request.getFinalPrice(),
                    true
            );
        }

        // 애플리케이션 서버별 시계 차이가 가격과 마감 판정에 섞이지 않게 DB 시각만 사용한다.
        LocalDateTime databaseNow = auctionRepository.findDatabaseNow();
        validatePurchasable(auction, databaseNow, buyerId);
        long finalPrice = calculateFinalPrice(auction, databaseNow);
        if (finalPrice <= 0) {
            throw new IllegalStateException("즉시구매 가격은 0보다 커야 합니다.");
        }

        // 가격을 계산한 요청 중 경매 DB의 상태·마감 조건을 먼저 바꾼 단 하나만 구매를 계속한다.
        if (auctionRepository.completeForInstantPurchase(auctionId) != 1) {
            throw new AuctionException(CONCURRENT_TRADE_CONFLICT);
        }

        // 잔액 확인과 전액 잠금을 한 문장으로 처리해 다른 경매와의 동시 구매도 초과 차감하지 않는다.
        if (memberRepository.movePointToLockedIfEnough(buyerId, finalPrice) != 1) {
            throw new AuctionException(INSUFFICIENT_DEPOSIT);
        }

        // Native CAS 이후 새 관리 참조로 보증금과 거래의 외래키를 연결한다.
        Auction completedAuction = auctionRepository.getReferenceById(auctionId);
        Member buyer = memberRepository.getReferenceById(buyerId);

        auctionDepositRepository.save(AuctionDeposit.builder()
                .member(buyer)
                .auction(completedAuction)
                .reservedAmount(finalPrice)
                .status(DepositStatus.HELD)
                .build());

        AuctionTrade trade = auctionTradeRepository.saveAndFlush(
                AuctionTrade.builder()
                        .auction(completedAuction)
                        .buyer(buyer)
                        .status(TradeStatus.PAID)
                        .finalPrice(finalPrice)
                        .build()
        );

        // 경매 CAS가 영속성 컨텍스트를 비웠으므로 멱등 요청을 다시 관리 상태로 조회한다.
        findIdempotencyRequestForUpdate(idempotencyKey).complete(trade, finalPrice);
        return new BuyNowResult(
                trade.getId(),
                auctionId,
                buyerId,
                finalPrice,
                false
        );
    }

    private InstantPurchaseRequest findIdempotencyRequestForUpdate(
            String idempotencyKey
    ) {
        return instantPurchaseRequestRepository
                .findByIdempotencyKeyForUpdate(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "선점한 즉시구매 멱등 요청을 찾을 수 없습니다."
                ));
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
            Auction auction,
            LocalDateTime databaseNow,
            Long buyerId
    ) {
        AuctionStatus status = auction.getStatus();
        if (status == AuctionStatus.COMPLETED) {
            throw new AuctionException(CONCURRENT_TRADE_CONFLICT);
        }
        if (status != AuctionStatus.OPEN && status != AuctionStatus.BID_ONGOING) {
            throw new AuctionException(AUCTION_NOT_ONGOING);
        }
        if (!auction.getEndedAt().isAfter(databaseNow)) {
            throw new AuctionException(AUCTION_ALREADY_ENDED);
        }
        if (auction.getSeller().getId().equals(buyerId)) {
            throw new AuctionException(SELF_PURCHASE_NOT_ALLOWED);
        }
    }

    private long calculateFinalPrice(
            Auction auction,
            LocalDateTime databaseNow
    ) {
        if (auction instanceof UpAuction upAuction) {
            return calculateUpAuctionPrice(upAuction);
        }
        if (auction instanceof DownAuction downAuction) {
            return calculateDownAuctionPrice(downAuction, databaseNow);
        }
        throw new IllegalStateException(
                "지원하지 않는 경매 유형입니다: " + auction.getClass().getSimpleName()
        );
    }

    private long calculateUpAuctionPrice(UpAuction auction) {
        Long buyNowPrice = auction.getBuyNowPrice();
        if (buyNowPrice == null) {
            throw new AuctionException(BUY_NOW_PRICE_NOT_SET);
        }
        return buyNowPrice;
    }

    private long calculateDownAuctionPrice(
            DownAuction auction,
            LocalDateTime databaseNow
    ) {
        long minimumPrice = auction.getMinimumPrice();
        long dropPrice = auction.getDropPrice();
        long dropIntervalMinutes = auction.getPriceDropInterval();
        if (dropPrice <= 0
                || dropIntervalMinutes <= 0
                || auction.getCreatedAt() == null
                || databaseNow == null
                || auction.getStartPrice() < minimumPrice) {
            throw new IllegalStateException("하향 경매 가격 조건이 올바르지 않습니다.");
        }

        long elapsedMinutes = Math.max(
                0,
                ChronoUnit.MINUTES.between(
                        auction.getCreatedAt(),
                        databaseNow
                )
        );
        long elapsedDrops = elapsedMinutes / dropIntervalMinutes;
        long priceRange = auction.getStartPrice() - minimumPrice;
        // 최저가까지 남은 금액이 하락폭으로 나누어떨어지지 않아도 마지막 하락을 한 회로 센다.
        long dropsToFloor = priceRange / dropPrice
                + (priceRange % dropPrice == 0 ? 0 : 1);

        if (elapsedDrops >= dropsToFloor) {
            return minimumPrice;
        }
        return auction.getStartPrice() - elapsedDrops * dropPrice;
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
