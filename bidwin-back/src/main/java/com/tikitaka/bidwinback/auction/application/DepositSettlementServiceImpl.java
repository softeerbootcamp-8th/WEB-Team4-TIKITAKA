package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionDeposit;
import com.tikitaka.bidwinback.auction.domain.enums.DepositStatus;
import com.tikitaka.bidwinback.auction.domain.exception.DepositException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionDepositRepository;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.DEPOSIT_ALREADY_SETTLED;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.DEPOSIT_AMOUNT_MISMATCH;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.DEPOSIT_NOT_FOUND;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INSUFFICIENT_DEPOSIT;

@Service
@RequiredArgsConstructor
public class DepositSettlementServiceImpl implements DepositSettlementService {

    private final AuctionDepositRepository auctionDepositRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public DepositFundingResult topUpToFinalPrice(Long auctionId, Long buyerId, long targetAmount) {
        if (targetAmount <= 0) {
            throw new IllegalArgumentException("보증금 목표 금액은 0보다 커야 합니다.");
        }

        AuctionDeposit deposit = auctionDepositRepository
                .findByAuctionIdAndMemberIdAndStatus(
                        auctionId,
                        buyerId,
                        DepositStatus.HELD
                )
                .orElseThrow(() -> new DepositException(DEPOSIT_NOT_FOUND));
        long current = deposit.getReservedAmount();

        // 이미 목표 이상 잠겨 있으면 추가 잠금 없이 멱등하게 끝낸다.
        if (targetAmount <= current) {
            return new DepositFundingResult(current, current, 0L);
        }

        long delta = targetAmount - current;
        if (memberRepository.movePointToLockedIfEnough(buyerId, delta) != 1) {
            throw new DepositException(INSUFFICIENT_DEPOSIT);
        }

        int increased = auctionDepositRepository.increaseReservedIfHeld(
                deposit.getId(), current, targetAmount);
        if (increased != 1) {
            throw new DepositException(DEPOSIT_ALREADY_SETTLED);
        }
        return new DepositFundingResult(current, targetAmount, delta);
    }

    @Override
    @Transactional
    public void refund(Long auctionId, Long buyerId, long expectedAmount) {
        settle(auctionId, buyerId, expectedAmount, DepositStatus.REFUNDED);

        int refunded = memberRepository.refundLockedPoint(buyerId, expectedAmount);
        if (refunded != 1) {
            throw new IllegalStateException("보증금 반환 중 잠금 포인트를 되돌리지 못했습니다.");
        }
    }

    @Override
    @Transactional
    public void transferToSeller(Long auctionId, Long buyerId, Long sellerId, long expectedAmount) {
        settle(auctionId, buyerId, expectedAmount, DepositStatus.USED);
        settleMemberPoints(buyerId, sellerId, expectedAmount,
                "보증금 정산 중 구매자 잠금 포인트를 차감하지 못했습니다.",
                "보증금 정산 중 판매자 잔액을 지급하지 못했습니다.");
    }

    @Override
    @Transactional
    public void forfeit(Long auctionId, Long buyerId, Long sellerId, long expectedAmount) {
        settle(auctionId, buyerId, expectedAmount, DepositStatus.FORFEITED);
        settleMemberPoints(buyerId, sellerId, expectedAmount,
                "보증금 몰수 중 잠금 포인트를 차감하지 못했습니다.",
                "보증금 몰수 중 판매자 잔액을 지급하지 못했습니다.");
    }

    // 구매자·판매자 회원 행을 항상 ID가 작은 쪽부터 잠가, 서로 반대 역할로 얽힌 다른 거래의
    // 정산과 동시에 실행돼도 순환 대기(데드락)가 생기지 않게 한다.
    private void settleMemberPoints(
            Long buyerId,
            Long sellerId,
            long expectedAmount,
            String buyerFailureMessage,
            String sellerFailureMessage
    ) {
        if (buyerId < sellerId) {
            forfeitBuyerPoint(buyerId, expectedAmount, buyerFailureMessage);
            creditSellerPoint(sellerId, expectedAmount, sellerFailureMessage);
        } else {
            creditSellerPoint(sellerId, expectedAmount, sellerFailureMessage);
            forfeitBuyerPoint(buyerId, expectedAmount, buyerFailureMessage);
        }
    }

    private void forfeitBuyerPoint(Long buyerId, long expectedAmount, String failureMessage) {
        int deducted = memberRepository.forfeitLockedPoint(buyerId, expectedAmount);
        if (deducted != 1) {
            throw new IllegalStateException(failureMessage);
        }
    }

    private void creditSellerPoint(Long sellerId, long expectedAmount, String failureMessage) {
        int credited = memberRepository.creditPoint(sellerId, expectedAmount);
        if (credited != 1) {
            throw new IllegalStateException(failureMessage);
        }
    }

    // HELD이고 예약 금액이 기대치와 같을 때만 원자적으로 상태를 전이한다.
    private void settle(Long auctionId, Long buyerId, long expectedAmount, DepositStatus status) {
        AuctionDeposit deposit = auctionDepositRepository
                .findByAuctionIdAndMemberId(auctionId, buyerId)
                .orElseThrow(() -> new DepositException(DEPOSIT_NOT_FOUND));

        if (deposit.getStatus() != DepositStatus.HELD) {
            throw new DepositException(DEPOSIT_ALREADY_SETTLED);
        }

        if (deposit.getReservedAmount() != expectedAmount) {
            throw new DepositException(DEPOSIT_AMOUNT_MISMATCH);
        }

        int settled = auctionDepositRepository.settleIfHeldWithAmount(
                deposit.getId(), status.name(), expectedAmount);
        if (settled != 1) {
            throw new DepositException(DEPOSIT_ALREADY_SETTLED);
        }
    }
}
