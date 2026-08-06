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
}
