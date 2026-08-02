package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.AuctionDeposit;
import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.entity.Bid;
import com.tikitaka.bidwinback.auction.domain.entity.InstantPurchaseRequest;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.domain.exception.BidException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionDepositRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionTradeRepository;
import com.tikitaka.bidwinback.auction.domain.repository.BidRepository;
import com.tikitaka.bidwinback.auction.domain.repository.InstantPurchaseRequestRepository;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import com.tikitaka.bidwinback.member.domain.exception.MemberException;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
    private final BidRepository bidRepository;
    private final InstantPurchaseRequestRepository requestRepository;
    private final BuyNowPriceCalculator priceCalculator;

    @Transactional
    public BuyNowResult buy(
            Long memberId,
            Long auctionId,
            String idempotencyKey
    ) {

        // 멱등 키 저장
        requestRepository.insertOrKeep(idempotencyKey, memberId, auctionId);
        // 멱등 키 비관락을 조회, 동시요청은 여기서 앞썬 트랜잭션이 락을 풀어야 진입가능
        InstantPurchaseRequest request = findRequestForUpdate(idempotencyKey);
        validateIdempotencyKey(request, memberId, auctionId);

        // 앞선 트랜잭션이 커밋되면 후에 들어온 트랜잭션은 대기했다가 앞 트랜잭션이 커밋된것이므로
        // 변경된 값을 읽어옴 따라서 여기서 바로 isComplted()가 true가 된다.
        if (request.isCompleted()) {
            return BuyNowResult.from(request.getTrade());
        }

        LocalDateTime purchasedAt = auctionRepository.currentDatabaseTime();
        Member buyer = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MEMBER_NOT_FOUND));
        Auction auction = auctionRepository.findWithSellerById(auctionId)
                .orElseThrow(() -> new AuctionException(AUCTION_NOT_FOUND));

        validateBuyer(buyer, auction);
        validateAuction(auction, purchasedAt);

        // 요구사항: 서버 간 시각 차이 없이 DB가 확정한 완료 시각으로 최종가를 계산한다.
        long finalPrice = priceCalculator.calculate(auction, purchasedAt);
        if (finalPrice <= 0) {
            throw new IllegalStateException("즉시구매 가격은 0보다 커야 합니다.");
        }

        // 잔액 확인과 전액 잠금을 한 UPDATE로 처리해 동시 구매의 초과 사용을 막는다.
        int lockedPoints = memberRepository.movePointToLockedIfEnough(
                memberId,
                finalPrice
        );

        if (lockedPoints != 1) {
            throw new BidException(INSUFFICIENT_DEPOSIT);
        }

        // 요구사항: 동시 구매 시 DB 조건부 갱신에 성공한 한 요청만 낙찰된다.
        int completed = auctionRepository.completeForBuyNow(auctionId, memberId, purchasedAt);
        if (completed != 1) {
            throw new BidException(CONCURRENT_TRADE_CONFLICT);
        }

        auctionDepositRepository.save(AuctionDeposit.builder()
                .member(buyer)
                .auction(auction)
                .reservedAmount(finalPrice)
                .build());

        bidRepository.save(Bid.builder()
                .auction(auction)
                .bidder(buyer)
                .price(finalPrice)
                .status(BidStatus.BUY_NOW)
                .build());

        // 거래와 멱등 요청을 함께 완료해 재요청 결과를 동일하게 보장한다.
        AuctionTrade trade = auctionTradeRepository.save(
                AuctionTrade.builder()
                        .auction(auction)
                        .buyer(buyer)
                        .status(TradeStatus.CONFIRMED)
                        .finalPrice(finalPrice)
                        .purchasedAt(purchasedAt)
                        .build()
        );
        request.complete(trade, finalPrice);

        return BuyNowResult.from(trade);
    }

    private InstantPurchaseRequest findRequestForUpdate(
            String idempotencyKey
    ) {
        return requestRepository.findByIdempotencyKeyForUpdate(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "선점한 즉시구매 멱등 요청을 찾을 수 없습니다."
                ));
    }

    private void validateIdempotencyKey(
            InstantPurchaseRequest request,
            Long memberId,
            Long auctionId
    ) {
        // 요구사항: 하나의 멱등 키를 다른 회원이나 경매 요청에 재사용할 수 없다.
        if (!request.belongsTo(memberId, auctionId)) {
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

    private void validateAuction(Auction auction, LocalDateTime databaseTime) {
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
        if (!auction.getEndedAt().isAfter(databaseTime)) {
            throw new AuctionException(AUCTION_ALREADY_ENDED);
        }
    }

}
