package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionDeposit;
import com.tikitaka.bidwinback.auction.domain.enums.DepositStatus;
import com.tikitaka.bidwinback.auction.domain.exception.DepositException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionDepositRepository;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

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

    @Mock
    private AuctionDepositRepository auctionDepositRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AuctionDeposit deposit;

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
}
