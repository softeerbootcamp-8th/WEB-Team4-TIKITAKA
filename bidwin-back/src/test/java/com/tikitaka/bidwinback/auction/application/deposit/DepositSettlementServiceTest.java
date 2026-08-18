package com.tikitaka.bidwinback.auction.application.deposit;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionDeposit;
import com.tikitaka.bidwinback.auction.domain.enums.DepositStatus;
import com.tikitaka.bidwinback.auction.domain.exception.DepositException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionDepositRepository;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.DEPOSIT_ALREADY_SETTLED;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.DEPOSIT_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepositSettlementServiceTest {

    private static final Long AUCTION_ID = 42L;
    private static final Long BUYER_ID = 1L;
    private static final Long DEPOSIT_ID = 7L;
    private static final Long SECOND_DEPOSIT_ID = 8L;
    private static final Long LOSER_ID = 2L;

    @Mock
    private AuctionDepositRepository auctionDepositRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AuctionDeposit deposit;

    @Mock
    private AuctionDeposit secondDeposit;

    @Mock
    private Member loser;

    private DepositSettlementService service;

    @BeforeEach
    void setUp() {
        service = new DepositSettlementServiceImpl(
                auctionDepositRepository,
                memberRepository
        );
    }

    @Test
    void 기존_경매_보증금이_있으면_낙찰가와의_차액만_추가로_잠긴다() {
        // given
        long currentAmount = 30_000L;
        long finalPrice = 150_000L;
        long difference = finalPrice - currentAmount;
        when(auctionDepositRepository.findByAuctionIdAndMemberIdAndStatus(
                AUCTION_ID,
                BUYER_ID,
                DepositStatus.HELD
        )).thenReturn(Optional.of(deposit));
        when(deposit.getId()).thenReturn(DEPOSIT_ID);
        when(deposit.getReservedAmount()).thenReturn(currentAmount);
        when(memberRepository.movePointToLockedIfEnough(BUYER_ID, difference))
                .thenReturn(1);
        when(auctionDepositRepository.increaseReservedIfHeld(
                DEPOSIT_ID,
                currentAmount,
                finalPrice
        )).thenReturn(1);

        // when
        DepositFundingResult result = service.topUpToFinalPrice(
                AUCTION_ID,
                BUYER_ID,
                finalPrice
        );

        // then
        assertAll(
                () -> assertThat(result.previousReserved()).isEqualTo(currentAmount),
                () -> assertThat(result.reservedAmount()).isEqualTo(finalPrice),
                () -> assertThat(result.addedAmount()).isEqualTo(difference)
        );
        verify(memberRepository).movePointToLockedIfEnough(BUYER_ID, difference);
        verify(auctionDepositRepository).increaseReservedIfHeld(
                DEPOSIT_ID,
                currentAmount,
                finalPrice
        );
        verify(auctionDepositRepository, never()).save(any(AuctionDeposit.class));
    }

    @Test
    void 해당_경매의_보증금이_없으면_새로_만들지_않고_구매를_거부한다() {
        // given
        when(auctionDepositRepository.findByAuctionIdAndMemberIdAndStatus(
                AUCTION_ID,
                BUYER_ID,
                DepositStatus.HELD
        )).thenReturn(Optional.empty());

        // when
        DepositException exception = assertThrows(
                DepositException.class,
                () -> service.topUpToFinalPrice(AUCTION_ID, BUYER_ID, 150_000L)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(DEPOSIT_NOT_FOUND);
        verify(auctionDepositRepository, never()).save(any(AuctionDeposit.class));
    }

    @Test
    void 이미_정산한_보증금의_반환을_재시도하면_중복_정산으로_거부한다() {
        // given
        when(auctionDepositRepository.findByAuctionIdAndMemberId(AUCTION_ID, BUYER_ID))
                .thenReturn(Optional.of(deposit));
        when(deposit.getStatus()).thenReturn(DepositStatus.REFUNDED);

        // when
        DepositException exception = assertThrows(
                DepositException.class,
                () -> service.refund(AUCTION_ID, BUYER_ID, 30_000L)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(DEPOSIT_ALREADY_SETTLED);
    }

    @Test
    void 같은_회원의_여러_비낙찰_보증금은_합산해_한번에_반환한다() {
        // given
        List<Long> auctionIds = List.of(AUCTION_ID, 43L);
        long firstAmount = 30_000L;
        long secondAmount = 40_000L;
        when(auctionDepositRepository.findLosingDeposits(
                auctionIds, DepositStatus.HELD
        ))
                .thenReturn(List.of(deposit, secondDeposit));
        when(deposit.getId()).thenReturn(DEPOSIT_ID);
        when(deposit.getMember()).thenReturn(loser);
        when(deposit.getReservedAmount()).thenReturn(firstAmount);
        when(deposit.getStatus()).thenReturn(DepositStatus.HELD);
        when(loser.getId()).thenReturn(LOSER_ID);
        when(secondDeposit.getId()).thenReturn(SECOND_DEPOSIT_ID);
        when(secondDeposit.getMember()).thenReturn(loser);
        when(secondDeposit.getReservedAmount()).thenReturn(secondAmount);
        when(secondDeposit.getStatus()).thenReturn(DepositStatus.HELD);
        when(auctionDepositRepository.settleIfHeldWithAmount(
                DEPOSIT_ID, DepositStatus.REFUNDED.name(), firstAmount
        )).thenReturn(1);
        when(auctionDepositRepository.settleIfHeldWithAmount(
                SECOND_DEPOSIT_ID, DepositStatus.REFUNDED.name(), secondAmount
        )).thenReturn(1);
        when(memberRepository.refundLockedPoint(
                LOSER_ID, firstAmount + secondAmount
        )).thenReturn(1);

        // when
        service.refundLosingDeposits(auctionIds);

        // then
        verify(auctionDepositRepository).findLosingDeposits(
                auctionIds, DepositStatus.HELD);
        verify(auctionDepositRepository).settleIfHeldWithAmount(
                DEPOSIT_ID, DepositStatus.REFUNDED.name(), firstAmount);
        verify(auctionDepositRepository).settleIfHeldWithAmount(
                SECOND_DEPOSIT_ID, DepositStatus.REFUNDED.name(), secondAmount);
        verify(memberRepository).refundLockedPoint(
                LOSER_ID, firstAmount + secondAmount);
    }
}
