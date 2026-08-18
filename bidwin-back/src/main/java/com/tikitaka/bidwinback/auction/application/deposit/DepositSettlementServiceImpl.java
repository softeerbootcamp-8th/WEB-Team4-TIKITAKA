package com.tikitaka.bidwinback.auction.application.deposit;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionDeposit;
import com.tikitaka.bidwinback.auction.domain.enums.DepositStatus;
import com.tikitaka.bidwinback.auction.domain.exception.DepositException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionDepositRepository;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.DEPOSIT_ALREADY_SETTLED;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.DEPOSIT_AMOUNT_MISMATCH;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.DEPOSIT_CONCURRENT_CONFLICT;
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
        if (lockDepositPoint(buyerId, delta) != 1) {
            throw new DepositException(INSUFFICIENT_DEPOSIT);
        }

        int increased = auctionDepositRepository.increaseReservedIfHeld(
                deposit.getId(), current, targetAmount);
        if (increased != 1) {
            throw new DepositException(DEPOSIT_ALREADY_SETTLED);
        }
        return new DepositFundingResult(current, targetAmount, delta);
    }

    // 이 쿼리엔 짧은 쿼리 타임아웃이 걸려 있어(입찰/즉시구매와 같은 회원 행을 다툴 수 있음),
    // 락 대기가 길어지면 500 대신 재시도 가능한 충돌 응답으로 변환한다.
    private int lockDepositPoint(Long buyerId, long amount) {
        try {
            return memberRepository.movePointToLockedIfEnough(buyerId, amount);
        } catch (PessimisticLockingFailureException | QueryTimeoutException exception) {
            throw new DepositException(DEPOSIT_CONCURRENT_CONFLICT);
        }
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
    public void refundLosingDeposits(List<Long> auctionIds) {
        if (auctionIds.isEmpty()) {
            return;
        }

        List<AuctionDeposit> deposits = auctionDepositRepository
                .findLosingDeposits(auctionIds, DepositStatus.HELD);
        Map<Long, Long> refundAmountsByMember = new TreeMap<>();

        for (AuctionDeposit deposit : deposits) {
            long amount = deposit.getReservedAmount();
            settle(deposit, amount, DepositStatus.REFUNDED);
            refundAmountsByMember.merge(
                    deposit.getMember().getId(),
                    amount,
                    (current, added) -> Math.addExact(current, added)
            );
        }

        // 한 회원이 같은 마감 배치의 여러 경매에서 탈락해도 회원 행은 한 번만 갱신한다.
        // 회원 ID 순서로 갱신해 다른 정산 트랜잭션과의 회원 행 잠금 순서를 통일한다.
        for (Map.Entry<Long, Long> refund : refundAmountsByMember.entrySet()) {
            if (memberRepository.refundLockedPoint(refund.getKey(), refund.getValue()) != 1) {
                throw new IllegalStateException("보증금 반환 중 잠금 포인트를 되돌리지 못했습니다.");
            }
        }
    }

    @Override
    @Transactional
    public void transferToSeller(Long auctionId, Long buyerId, Long sellerId, long expectedAmount) {
        settle(auctionId, buyerId, expectedAmount, DepositStatus.USED);

        if (isBuyerIdSmaller(buyerId, sellerId)) {
            if (memberRepository.forfeitLockedPoint(buyerId, expectedAmount) != 1) {
                throw new IllegalStateException("보증금 정산 중 구매자 잠금 포인트를 차감하지 못했습니다.");
            }
            if (memberRepository.creditPoint(sellerId, expectedAmount) != 1) {
                throw new IllegalStateException("보증금 정산 중 판매자 잔액을 지급하지 못했습니다.");
            }
        } else {
            if (memberRepository.creditPoint(sellerId, expectedAmount) != 1) {
                throw new IllegalStateException("보증금 정산 중 판매자 잔액을 지급하지 못했습니다.");
            }
            if (memberRepository.forfeitLockedPoint(buyerId, expectedAmount) != 1) {
                throw new IllegalStateException("보증금 정산 중 구매자 잠금 포인트를 차감하지 못했습니다.");
            }
        }
    }

    @Override
    @Transactional
    public void forfeit(Long auctionId, Long buyerId, Long sellerId, long expectedAmount) {
        settle(auctionId, buyerId, expectedAmount, DepositStatus.FORFEITED);

        if (isBuyerIdSmaller(buyerId, sellerId)) {
            if (memberRepository.forfeitLockedPoint(buyerId, expectedAmount) != 1) {
                throw new IllegalStateException("보증금 몰수 중 잠금 포인트를 차감하지 못했습니다.");
            }
            if (memberRepository.creditPoint(sellerId, expectedAmount) != 1) {
                throw new IllegalStateException("보증금 몰수 중 판매자 잔액을 지급하지 못했습니다.");
            }
        } else {
            if (memberRepository.creditPoint(sellerId, expectedAmount) != 1) {
                throw new IllegalStateException("보증금 몰수 중 판매자 잔액을 지급하지 못했습니다.");
            }
            if (memberRepository.forfeitLockedPoint(buyerId, expectedAmount) != 1) {
                throw new IllegalStateException("보증금 몰수 중 잠금 포인트를 차감하지 못했습니다.");
            }
        }
    }

    // 구매자 ID가 판매자 ID보다 작은지에 따라 회원 행 잠금 순서를 정한다. 서로 반대 역할로 얽힌
    // 두 거래가 동시에 정산/몰수돼도 항상 작은 ID의 회원부터 잠그게 해 순환 대기(데드락)를 막는다.
    private boolean isBuyerIdSmaller(Long buyerId, Long sellerId) {
        return buyerId < sellerId;
    }

    // HELD이고 예약 금액이 기대치와 같을 때만 원자적으로 상태를 전이한다.
    private void settle(Long auctionId, Long buyerId, long expectedAmount, DepositStatus status) {
        AuctionDeposit deposit = auctionDepositRepository
                .findByAuctionIdAndMemberId(auctionId, buyerId)
                .orElseThrow(() -> new DepositException(DEPOSIT_NOT_FOUND));

        settle(deposit, expectedAmount, status);
    }

    private void settle(AuctionDeposit deposit, long expectedAmount, DepositStatus status) {
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
