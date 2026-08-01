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
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuyNowServiceTest {

    private static final Long AUCTION_ID = 1L;
    private static final Long BUYER_ID = 2L;
    private static final Long SELLER_ID = 3L;
    private static final String IDEMPOTENCY_KEY = "018f6c2d-7d91-7f15-a7ec-8d90f5c58c29";
    private static final LocalDateTime DATABASE_NOW =
            LocalDateTime.of(2026, 8, 1, 12, 31);

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AuctionDepositRepository auctionDepositRepository;

    @Mock
    private AuctionTradeRepository auctionTradeRepository;

    @Mock
    private InstantPurchaseRequestRepository instantPurchaseRequestRepository;

    private BuyNowService buyNowService;

    @BeforeEach
    void setUp() {
        buyNowService = new BuyNowService(
                auctionRepository,
                memberRepository,
                auctionDepositRepository,
                auctionTradeRepository,
                instantPurchaseRequestRepository
        );
    }

    @Test
    void 하향_경매는_DB_시각으로_계산한_가격에_즉시구매된다() {
        // given
        DownAuction auction = activeDownAuction();
        when(auction.getStartPrice()).thenReturn(100_000L);
        when(auction.getCreatedAt()).thenReturn(DATABASE_NOW.minusMinutes(31));
        when(auction.getMinimumPrice()).thenReturn(50_000L);
        when(auction.getDropPrice()).thenReturn(10_000L);
        when(auction.getPriceDropInterval()).thenReturn(10L);
        InstantPurchaseRequest idempotencyRequest = givenNewRequest(auction);
        AuctionTrade savedTrade = givenSuccessfulPersistence(11L);

        // when
        BuyNowService.BuyNowResult result = buyNowService.purchase(
                AUCTION_ID,
                BUYER_ID,
                IDEMPOTENCY_KEY
        );

        // then
        assertThat(result.tradeId()).isEqualTo(11L);
        assertThat(result.finalPrice()).isEqualTo(70_000L);
        assertThat(result.replayed()).isFalse();
        verify(memberRepository).movePointToLockedIfEnough(BUYER_ID, 70_000L);
        verify(idempotencyRequest).complete(savedTrade, 70_000L);

        ArgumentCaptor<AuctionDeposit> depositCaptor =
                ArgumentCaptor.forClass(AuctionDeposit.class);
        verify(auctionDepositRepository).save(depositCaptor.capture());
        assertThat(depositCaptor.getValue().getReservedAmount()).isEqualTo(70_000L);
        assertThat(depositCaptor.getValue().getStatus()).isEqualTo(DepositStatus.HELD);

        ArgumentCaptor<AuctionTrade> tradeCaptor =
                ArgumentCaptor.forClass(AuctionTrade.class);
        verify(auctionTradeRepository).saveAndFlush(tradeCaptor.capture());
        assertThat(tradeCaptor.getValue().getFinalPrice()).isEqualTo(70_000L);
        assertThat(tradeCaptor.getValue().getStatus()).isEqualTo(TradeStatus.PAID);
    }

    @Test
    void 하향_경매의_계산값이_최저가보다_낮으면_최저가로_구매된다() {
        // given
        DownAuction auction = activeDownAuction();
        when(auction.getStartPrice()).thenReturn(100_000L);
        when(auction.getCreatedAt()).thenReturn(DATABASE_NOW.minusHours(10));
        when(auction.getMinimumPrice()).thenReturn(50_000L);
        when(auction.getDropPrice()).thenReturn(10_000L);
        when(auction.getPriceDropInterval()).thenReturn(10L);
        givenNewRequest(auction);
        givenSuccessfulPersistence(12L);

        // when
        BuyNowService.BuyNowResult result = buyNowService.purchase(
                AUCTION_ID,
                BUYER_ID,
                IDEMPOTENCY_KEY
        );

        // then
        assertThat(result.finalPrice()).isEqualTo(50_000L);
        verify(memberRepository).movePointToLockedIfEnough(BUYER_ID, 50_000L);
    }

    @Test
    void 상향_경매는_DB에_저장된_즉시구매가로_구매된다() {
        // given
        UpAuction auction = activeUpAuction();
        when(auction.getBuyNowPrice()).thenReturn(320_000L);
        givenNewRequest(auction);
        givenSuccessfulPersistence(13L);

        // when
        BuyNowService.BuyNowResult result = buyNowService.purchase(
                AUCTION_ID,
                BUYER_ID,
                IDEMPOTENCY_KEY
        );

        // then
        assertThat(result.finalPrice()).isEqualTo(320_000L);
        verify(memberRepository).movePointToLockedIfEnough(BUYER_ID, 320_000L);
    }

    @Test
    void 완료된_멱등요청을_재시도하면_저장된_결과만_반환한다() {
        // given
        Auction auction = mock(Auction.class);
        AuctionTrade savedTrade = mock(AuctionTrade.class);
        InstantPurchaseRequest request = mock(InstantPurchaseRequest.class);
        when(auctionRepository.findById(AUCTION_ID))
                .thenReturn(Optional.of(auction));
        when(instantPurchaseRequestRepository.findByIdempotencyKeyForUpdate(
                IDEMPOTENCY_KEY
        )).thenReturn(Optional.of(request));
        when(request.belongsTo(BUYER_ID, AUCTION_ID)).thenReturn(true);
        when(request.isCompleted()).thenReturn(true);
        when(request.getTrade()).thenReturn(savedTrade);
        when(savedTrade.getId()).thenReturn(21L);
        when(request.getFinalPrice()).thenReturn(150_000L);

        // when
        BuyNowService.BuyNowResult result = buyNowService.purchase(
                AUCTION_ID,
                BUYER_ID,
                IDEMPOTENCY_KEY
        );

        // then
        assertThat(result.tradeId()).isEqualTo(21L);
        assertThat(result.finalPrice()).isEqualTo(150_000L);
        assertThat(result.replayed()).isTrue();
        verify(auctionRepository, never()).completeForInstantPurchase(any());
        verifyNoInteractions(auctionDepositRepository, auctionTradeRepository);
    }

    @Test
    void 선점한_멱등요청을_조회할_수_없으면_구매를_중단한다() {
        // given
        Auction auction = mock(Auction.class);
        when(auctionRepository.findById(AUCTION_ID))
                .thenReturn(Optional.of(auction));
        when(instantPurchaseRequestRepository.findByIdempotencyKeyForUpdate(
                IDEMPOTENCY_KEY
        )).thenReturn(Optional.empty());

        // when
        // then
        assertThatIllegalStateException()
                .isThrownBy(() -> buyNowService.purchase(
                        AUCTION_ID,
                        BUYER_ID,
                        IDEMPOTENCY_KEY
                ));
        verify(auctionRepository, never()).completeForInstantPurchase(any());
        verifyNoInteractions(
                memberRepository,
                auctionDepositRepository,
                auctionTradeRepository
        );
    }

    @Test
    void 다른_요청이_먼저_경매를_마감하면_즉시구매는_충돌로_실패한다() {
        // given
        UpAuction auction = activeUpAuction();
        when(auction.getBuyNowPrice()).thenReturn(320_000L);
        givenNewRequest(auction);
        when(auctionRepository.completeForInstantPurchase(AUCTION_ID)).thenReturn(0);

        // when
        // then
        assertThatExceptionOfType(AuctionException.class)
                .isThrownBy(() -> buyNowService.purchase(
                        AUCTION_ID,
                        BUYER_ID,
                        IDEMPOTENCY_KEY
                ))
                .extracting(AuctionException::getErrorCode)
                .isEqualTo(ErrorCode.CONCURRENT_TRADE_CONFLICT);
        verify(memberRepository, never()).movePointToLockedIfEnough(any(), anyLong());
        verifyNoInteractions(auctionDepositRepository, auctionTradeRepository);
    }

    @Test
    void 이미_구매_완료된_경매의_새_즉시구매는_충돌로_실패한다() {
        // given
        Auction auction = mock(Auction.class);
        when(auction.getStatus()).thenReturn(AuctionStatus.COMPLETED);
        givenNewRequest(auction);

        // when
        // then
        assertThatExceptionOfType(AuctionException.class)
                .isThrownBy(() -> buyNowService.purchase(
                        AUCTION_ID,
                        BUYER_ID,
                        IDEMPOTENCY_KEY
                ))
                .extracting(AuctionException::getErrorCode)
                .isEqualTo(ErrorCode.CONCURRENT_TRADE_CONFLICT);
        verify(auctionRepository, never()).completeForInstantPurchase(any());
    }

    @Test
    void DB_시각이_종료시각과_같으면_즉시구매할_수_없다() {
        // given
        Auction auction = mock(Auction.class);
        when(auction.getStatus()).thenReturn(AuctionStatus.OPEN);
        when(auction.getEndedAt()).thenReturn(DATABASE_NOW);
        givenNewRequest(auction);

        // when
        // then
        assertThatExceptionOfType(AuctionException.class)
                .isThrownBy(() -> buyNowService.purchase(
                        AUCTION_ID,
                        BUYER_ID,
                        IDEMPOTENCY_KEY
                ))
                .extracting(AuctionException::getErrorCode)
                .isEqualTo(ErrorCode.AUCTION_ALREADY_ENDED);
        verify(auctionRepository, never()).completeForInstantPurchase(any());
    }

    @Test
    void 구매대금이_부족하면_거래와_보증금을_생성하지_않는다() {
        // given
        UpAuction auction = activeUpAuction();
        when(auction.getBuyNowPrice()).thenReturn(320_000L);
        givenNewRequest(auction);
        when(auctionRepository.completeForInstantPurchase(AUCTION_ID)).thenReturn(1);
        when(memberRepository.movePointToLockedIfEnough(BUYER_ID, 320_000L))
                .thenReturn(0);

        // when
        // then
        assertThatExceptionOfType(AuctionException.class)
                .isThrownBy(() -> buyNowService.purchase(
                        AUCTION_ID,
                        BUYER_ID,
                        IDEMPOTENCY_KEY
                ))
                .extracting(AuctionException::getErrorCode)
                .isEqualTo(ErrorCode.INSUFFICIENT_DEPOSIT);
        verifyNoInteractions(auctionDepositRepository, auctionTradeRepository);
    }

    @Test
    void 판매자는_자신의_경매를_즉시구매할_수_없다() {
        // given
        Auction auction = mock(Auction.class);
        Member seller = mock(Member.class);
        when(auction.getStatus()).thenReturn(AuctionStatus.OPEN);
        when(auction.getEndedAt()).thenReturn(DATABASE_NOW.plusMinutes(1));
        when(auction.getSeller()).thenReturn(seller);
        when(seller.getId()).thenReturn(BUYER_ID);
        givenNewRequest(auction);

        // when
        // then
        assertThatExceptionOfType(AuctionException.class)
                .isThrownBy(() -> buyNowService.purchase(
                        AUCTION_ID,
                        BUYER_ID,
                        IDEMPOTENCY_KEY
                ))
                .extracting(AuctionException::getErrorCode)
                .isEqualTo(ErrorCode.SELF_PURCHASE_NOT_ALLOWED);
        verify(auctionRepository, never()).completeForInstantPurchase(any());
    }

    @Test
    void 즉시구매가가_없는_상향_경매는_즉시구매할_수_없다() {
        // given
        UpAuction auction = activeUpAuction();
        when(auction.getBuyNowPrice()).thenReturn(null);
        givenNewRequest(auction);

        // when
        // then
        assertThatExceptionOfType(AuctionException.class)
                .isThrownBy(() -> buyNowService.purchase(
                        AUCTION_ID,
                        BUYER_ID,
                        IDEMPOTENCY_KEY
                ))
                .extracting(AuctionException::getErrorCode)
                .isEqualTo(ErrorCode.BUY_NOW_PRICE_NOT_SET);
        verify(auctionRepository, never()).completeForInstantPurchase(any());
    }

    @Test
    void DB의_즉시구매가가_0_이하면_포인트를_변경하지_않는다() {
        // given
        UpAuction auction = activeUpAuction();
        when(auction.getBuyNowPrice()).thenReturn(0L);
        givenNewRequest(auction);

        // when
        // then
        assertThatIllegalStateException()
                .isThrownBy(() -> buyNowService.purchase(
                        AUCTION_ID,
                        BUYER_ID,
                        IDEMPOTENCY_KEY
                ));
        verify(auctionRepository, never()).completeForInstantPurchase(any());
        verify(memberRepository, never()).movePointToLockedIfEnough(any(), anyLong());
    }

    @Test
    void 존재하지_않는_경매는_즉시구매할_수_없다() {
        // given
        when(auctionRepository.findById(AUCTION_ID))
                .thenReturn(Optional.empty());

        // when
        // then
        assertThatExceptionOfType(AuctionException.class)
                .isThrownBy(() -> buyNowService.purchase(
                        AUCTION_ID,
                        BUYER_ID,
                        IDEMPOTENCY_KEY
                ))
                .extracting(AuctionException::getErrorCode)
                .isEqualTo(ErrorCode.AUCTION_NOT_FOUND);
        verifyNoInteractions(instantPurchaseRequestRepository);
    }

    @Test
    void 공백이_포함된_멱등키는_즉시구매에_사용할_수_없다() {
        // given
        String invalidKey = "invalid key";

        // when
        // then
        assertThatExceptionOfType(AuctionException.class)
                .isThrownBy(() -> buyNowService.purchase(
                        AUCTION_ID,
                        BUYER_ID,
                        invalidKey
                ))
                .extracting(AuctionException::getErrorCode)
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verifyNoInteractions(auctionRepository, instantPurchaseRequestRepository);
    }

    private UpAuction activeUpAuction() {
        UpAuction auction = mock(UpAuction.class);
        givenActiveAuction(auction);
        return auction;
    }

    private DownAuction activeDownAuction() {
        DownAuction auction = mock(DownAuction.class);
        givenActiveAuction(auction);
        return auction;
    }

    private void givenActiveAuction(Auction auction) {
        Member seller = mock(Member.class);
        when(auction.getStatus()).thenReturn(AuctionStatus.BID_ONGOING);
        when(auction.getEndedAt()).thenReturn(DATABASE_NOW.plusHours(1));
        when(auction.getSeller()).thenReturn(seller);
        when(seller.getId()).thenReturn(SELLER_ID);
    }

    private InstantPurchaseRequest givenNewRequest(Auction auction) {
        InstantPurchaseRequest request = mock(InstantPurchaseRequest.class);
        when(auctionRepository.findById(AUCTION_ID))
                .thenReturn(Optional.of(auction));
        when(auctionRepository.findDatabaseNow()).thenReturn(DATABASE_NOW);
        when(instantPurchaseRequestRepository.findByIdempotencyKeyForUpdate(
                IDEMPOTENCY_KEY
        )).thenReturn(Optional.of(request));
        when(request.belongsTo(BUYER_ID, AUCTION_ID)).thenReturn(true);
        return request;
    }

    private AuctionTrade givenSuccessfulPersistence(Long tradeId) {
        Auction auction = mock(Auction.class);
        Member buyer = mock(Member.class);
        AuctionTrade savedTrade = mock(AuctionTrade.class);
        when(savedTrade.getId()).thenReturn(tradeId);
        when(auctionRepository.completeForInstantPurchase(AUCTION_ID)).thenReturn(1);
        when(memberRepository.movePointToLockedIfEnough(eq(BUYER_ID), anyLong()))
                .thenReturn(1);
        when(auctionRepository.getReferenceById(AUCTION_ID)).thenReturn(auction);
        when(memberRepository.getReferenceById(BUYER_ID)).thenReturn(buyer);
        when(auctionTradeRepository.saveAndFlush(any(AuctionTrade.class)))
                .thenReturn(savedTrade);
        return savedTrade;
    }

}
