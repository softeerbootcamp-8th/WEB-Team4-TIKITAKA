package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.entity.BuyNowRequestLog;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.DepositStatus;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_ALREADY_TRADED;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_NOT_ONGOING;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.CONCURRENT_TRADE_CONFLICT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.IDEMPOTENCY_KEY_REUSED;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INSUFFICIENT_DEPOSIT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.MEMBER_NOT_ACTIVE;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.SELF_PURCHASE_NOT_ALLOWED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuyNowServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long SELLER_ID = 2L;
    private static final Long AUCTION_ID = 42L;
    private static final Long OTHER_AUCTION_ID = 43L;
    private static final String IDEMPOTENCY_KEY = "buy-now-42-request-1";
    private static final LocalDateTime DATABASE_TIME =
            LocalDateTime.of(2026, 7, 30, 12, 0);
    private static final LocalDateTime ENDED_AT = DATABASE_TIME.plusHours(1);
    private static final LocalDateTime PURCHASED_AT =
            DATABASE_TIME.plusSeconds(1);
    private static final long FINAL_PRICE = 232_000L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private AuctionDepositRepository auctionDepositRepository;

    @Mock
    private AuctionTradeRepository auctionTradeRepository;

    @Mock
    private BuyNowIdempotencyStore idempotencyStore;

    @Mock
    private BuyNowPriceCalculator priceCalculator;

    @Mock
    private BuyNowTransactionService transactionBoundary;

    @Mock
    private Member buyer;

    @Mock
    private Member seller;

    @Mock
    private DownAuction auction;

    @Mock
    private BuyNowRequestLog requestLog;

    @Mock
    private AuctionTrade persistedTrade;

    private BuyNowTransactionService transactionService;
    private BuyNowService buyNowService;

    @BeforeEach
    void setUp() {
        transactionService = new BuyNowTransactionService(
                memberRepository,
                auctionRepository,
                auctionDepositRepository,
                auctionTradeRepository,
                idempotencyStore,
                priceCalculator
        );
        buyNowService = new BuyNowService(transactionBoundary);
    }

    @Test
    void OPEN_경매를_ACTIVE_회원이_HELD_보증금으로_즉시구매한다() {
        stubReadyToPurchase();
        when(auctionRepository.completeForBuyNow(AUCTION_ID, MEMBER_ID))
                .thenReturn(1);
        when(auctionRepository.findCompletedAt(AUCTION_ID))
                .thenReturn(Optional.of(PURCHASED_AT));
        when(priceCalculator.calculate(auction, PURCHASED_AT))
                .thenReturn(FINAL_PRICE);
        when(auctionDepositRepository.useHeldDeposit(MEMBER_ID, AUCTION_ID))
                .thenReturn(1);
        stubPersistedTrade();
        when(auctionTradeRepository.saveAndFlush(any(AuctionTrade.class)))
                .thenReturn(persistedTrade);

        BuyNowResult result = buyInTransaction();

        ArgumentCaptor<AuctionTrade> tradeCaptor =
                ArgumentCaptor.forClass(AuctionTrade.class);
        verify(auctionTradeRepository).saveAndFlush(tradeCaptor.capture());
        AuctionTrade trade = tradeCaptor.getValue();
        ArgumentCaptor<BuyNowRequestLog> requestCaptor =
                ArgumentCaptor.forClass(BuyNowRequestLog.class);
        verify(idempotencyStore).saveAndFlush(requestCaptor.capture());
        BuyNowRequestLog savedRequest = requestCaptor.getValue();
        assertAll(
                () -> assertThat(trade.getAuction()).isSameAs(auction),
                () -> assertThat(trade.getBuyer()).isSameAs(buyer),
                () -> assertThat(trade.getStatus())
                        .isEqualTo(TradeStatus.WAITING_PAYMENT),
                () -> assertThat(trade.getFinalPrice()).isEqualTo(FINAL_PRICE),
                () -> assertThat(trade.getPurchasedAt()).isEqualTo(PURCHASED_AT),
                () -> assertThat(savedRequest.getIdempotencyKey())
                        .isEqualTo(IDEMPOTENCY_KEY),
                () -> assertThat(savedRequest.getMember()).isSameAs(buyer),
                () -> assertThat(savedRequest.getAuction()).isSameAs(auction),
                () -> assertThat(savedRequest.getTrade()).isSameAs(persistedTrade),
                () -> assertThat(result.tradeId()).isEqualTo(7L),
                () -> assertThat(result.auctionId()).isEqualTo(AUCTION_ID),
                () -> assertThat(result.finalPrice()).isEqualTo(FINAL_PRICE),
                () -> assertThat(result.purchasedAt()).isEqualTo(PURCHASED_AT)
        );
        verify(auctionRepository).completeForBuyNow(AUCTION_ID, MEMBER_ID);
        verify(auctionDepositRepository).useHeldDeposit(MEMBER_ID, AUCTION_ID);
    }

    @Test
    void COMPLETED_경매는_즉시구매할_수_없다() {
        stubNewRequestWithLoadedEntities(AUCTION_ID);
        stubActiveBuyerWithDifferentSeller();
        when(auction.getStatus()).thenReturn(AuctionStatus.COMPLETED);

        AuctionException exception = assertThrows(
                AuctionException.class,
                this::buyInTransaction
        );

        assertThat(exception.getErrorCode()).isEqualTo(AUCTION_ALREADY_TRADED);
        verify(auctionRepository, never()).currentDatabaseTime();
        verifyNoPurchaseMutation();
        verifyNoInteractions(priceCalculator);
    }

    @Test
    void BID_ONGOING_경매는_즉시구매할_수_없다() {
        stubNewRequestWithLoadedEntities(AUCTION_ID);
        stubActiveBuyerWithDifferentSeller();
        when(auction.getStatus()).thenReturn(AuctionStatus.BID_ONGOING);

        AuctionException exception = assertThrows(
                AuctionException.class,
                this::buyInTransaction
        );

        assertThat(exception.getErrorCode()).isEqualTo(AUCTION_NOT_ONGOING);
        verify(auctionRepository, never()).currentDatabaseTime();
        verifyNoPurchaseMutation();
        verifyNoInteractions(priceCalculator);
    }

    @Test
    void 판매자는_자신의_경매를_즉시구매할_수_없다() {
        stubNewRequestWithLoadedEntities(AUCTION_ID);
        when(buyer.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(buyer.getId()).thenReturn(MEMBER_ID);
        when(auction.getSeller()).thenReturn(buyer);

        BidException exception = assertThrows(
                BidException.class,
                this::buyInTransaction
        );

        assertThat(exception.getErrorCode()).isEqualTo(SELF_PURCHASE_NOT_ALLOWED);
        verifyNoPurchaseMutation();
        verifyNoInteractions(priceCalculator);
    }

    @Test
    void ACTIVE가_아닌_회원은_즉시구매할_수_없다() {
        stubNewRequestWithLoadedEntities(AUCTION_ID);
        when(buyer.getStatus()).thenReturn(MemberStatus.BANNED);

        MemberException exception = assertThrows(
                MemberException.class,
                this::buyInTransaction
        );

        assertThat(exception.getErrorCode()).isEqualTo(MEMBER_NOT_ACTIVE);
        verifyNoPurchaseMutation();
        verifyNoInteractions(priceCalculator);
    }

    @Test
    void HELD_보증금이_없으면_즉시구매할_수_없다() {
        stubNewRequestWithLoadedEntities(AUCTION_ID);
        stubActiveBuyerWithDifferentSeller();
        stubOpenAuctionBeforeEnd();
        when(auctionDepositRepository
                .existsByMemberIdAndAuctionIdAndStatusAndReservedAmountGreaterThan(
                        MEMBER_ID,
                        AUCTION_ID,
                        DepositStatus.HELD,
                        0
                )).thenReturn(false);

        BidException exception = assertThrows(
                BidException.class,
                this::buyInTransaction
        );

        assertThat(exception.getErrorCode()).isEqualTo(INSUFFICIENT_DEPOSIT);
        verifyNoPurchaseMutation();
        verifyNoInteractions(priceCalculator);
    }

    @Test
    void 경매_CAS가_실패하면_동시구매_충돌로_처리한다() {
        stubReadyToPurchase();
        when(auctionRepository.completeForBuyNow(AUCTION_ID, MEMBER_ID))
                .thenReturn(0);

        BidException exception = assertThrows(
                BidException.class,
                this::buyInTransaction
        );

        assertThat(exception.getErrorCode()).isEqualTo(CONCURRENT_TRADE_CONFLICT);
        verify(auctionDepositRepository, never())
                .useHeldDeposit(anyLong(), anyLong());
        verify(auctionTradeRepository, never()).saveAndFlush(any());
        verify(idempotencyStore, never()).saveAndFlush(any());
        verifyNoInteractions(priceCalculator);
    }

    @Test
    void 완료된_동일_멱등_키를_재요청하면_기존_거래를_응답한다() {
        stubPersistedTrade();
        when(idempotencyStore.findByKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(requestLog));
        when(requestLog.matches(MEMBER_ID, AUCTION_ID)).thenReturn(true);
        when(requestLog.getTrade()).thenReturn(persistedTrade);

        BuyNowResult result = buyInTransaction();

        assertAll(
                () -> assertThat(result.tradeId()).isEqualTo(7L),
                () -> assertThat(result.auctionId()).isEqualTo(AUCTION_ID),
                () -> assertThat(result.finalPrice()).isEqualTo(FINAL_PRICE),
                () -> assertThat(result.purchasedAt()).isEqualTo(PURCHASED_AT)
        );
        verifyNoInteractions(
                memberRepository,
                auctionRepository,
                auctionDepositRepository,
                auctionTradeRepository,
                priceCalculator
        );
        verify(idempotencyStore, never()).saveAndFlush(any());
    }

    @Test
    void 동일_멱등_키를_다른_요청에_재사용하면_거부한다() {
        when(idempotencyStore.findByKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(requestLog));
        when(requestLog.matches(MEMBER_ID, OTHER_AUCTION_ID)).thenReturn(false);

        BidException exception = assertThrows(
                BidException.class,
                () -> transactionService.buy(
                        MEMBER_ID,
                        OTHER_AUCTION_ID,
                        IDEMPOTENCY_KEY
                )
        );

        assertThat(exception.getErrorCode()).isEqualTo(IDEMPOTENCY_KEY_REUSED);
        verifyNoInteractions(
                memberRepository,
                auctionRepository,
                auctionDepositRepository,
                auctionTradeRepository,
                priceCalculator
        );
        verify(idempotencyStore, never()).saveAndFlush(any());
    }

    @Test
    void CAS_패자는_동일_키의_완료된_거래를_재조회한다() {
        BidException conflict = new BidException(CONCURRENT_TRADE_CONFLICT);
        BuyNowResult expected = completedResult();
        when(transactionBoundary.buy(MEMBER_ID, AUCTION_ID, IDEMPOTENCY_KEY))
                .thenThrow(conflict);
        when(transactionBoundary.replay(MEMBER_ID, AUCTION_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(expected));

        BuyNowResult result = buyNowService.buy(
                MEMBER_ID,
                AUCTION_ID,
                IDEMPOTENCY_KEY
        );

        assertThat(result).isSameAs(expected);
        verify(transactionBoundary).replay(
                MEMBER_ID,
                AUCTION_ID,
                IDEMPOTENCY_KEY
        );
    }

    @Test
    void 멱등_로그_UNIQUE_경합은_완료된_거래를_재조회한다() {
        DataIntegrityViolationException uniqueConflict =
                new DataIntegrityViolationException("duplicate idempotency key");
        BuyNowResult expected = completedResult();
        when(transactionBoundary.buy(MEMBER_ID, AUCTION_ID, IDEMPOTENCY_KEY))
                .thenThrow(uniqueConflict);
        when(transactionBoundary.replay(MEMBER_ID, AUCTION_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(expected));

        BuyNowResult result = buyNowService.buy(
                MEMBER_ID,
                AUCTION_ID,
                IDEMPOTENCY_KEY
        );

        assertThat(result).isSameAs(expected);
        verify(transactionBoundary).replay(
                MEMBER_ID,
                AUCTION_ID,
                IDEMPOTENCY_KEY
        );
    }

    private BuyNowResult buyInTransaction() {
        return transactionService.buy(MEMBER_ID, AUCTION_ID, IDEMPOTENCY_KEY);
    }

    private BuyNowResult completedResult() {
        return new BuyNowResult(7L, AUCTION_ID, FINAL_PRICE, PURCHASED_AT);
    }

    private void stubNewRequestWithLoadedEntities(Long auctionId) {
        when(idempotencyStore.findByKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(buyer));
        when(auctionRepository.findWithSellerById(auctionId))
                .thenReturn(Optional.of(auction));
    }

    private void stubActiveBuyerWithDifferentSeller() {
        when(buyer.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(buyer.getId()).thenReturn(MEMBER_ID);
        when(auction.getSeller()).thenReturn(seller);
        when(seller.getId()).thenReturn(SELLER_ID);
    }

    private void stubOpenAuctionBeforeEnd() {
        when(auction.getStatus()).thenReturn(AuctionStatus.OPEN);
        when(auction.getEndedAt()).thenReturn(ENDED_AT);
        when(auctionRepository.currentDatabaseTime()).thenReturn(DATABASE_TIME);
    }

    private void stubReadyToPurchase() {
        stubNewRequestWithLoadedEntities(AUCTION_ID);
        stubActiveBuyerWithDifferentSeller();
        stubOpenAuctionBeforeEnd();
        when(auctionDepositRepository
                .existsByMemberIdAndAuctionIdAndStatusAndReservedAmountGreaterThan(
                        MEMBER_ID,
                        AUCTION_ID,
                        DepositStatus.HELD,
                        0
                )).thenReturn(true);
    }

    private void stubPersistedTrade() {
        when(persistedTrade.getId()).thenReturn(7L);
        when(persistedTrade.getAuction()).thenReturn(auction);
        when(auction.getId()).thenReturn(AUCTION_ID);
        when(persistedTrade.getFinalPrice()).thenReturn(FINAL_PRICE);
        when(persistedTrade.getPurchasedAt()).thenReturn(PURCHASED_AT);
    }

    private void verifyNoPurchaseMutation() {
        verify(auctionRepository, never())
                .completeForBuyNow(anyLong(), anyLong());
        verify(auctionDepositRepository, never())
                .useHeldDeposit(anyLong(), anyLong());
        verify(auctionTradeRepository, never()).saveAndFlush(any());
        verify(idempotencyStore, never()).saveAndFlush(any());
    }
}
