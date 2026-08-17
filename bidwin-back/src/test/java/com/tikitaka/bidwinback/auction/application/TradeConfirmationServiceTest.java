package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.application.live.TradeStatusChanged;
import com.tikitaka.bidwinback.auction.application.live.TradeLiveState;
import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
import com.tikitaka.bidwinback.auction.domain.exception.TradeException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionTradeRepository;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_TRADE_STATUS_TRANSITION;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.TRADE_ACCESS_DENIED;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.TRADE_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeConfirmationServiceTest {

    private static final Long TRADE_ID = 7L;
    private static final Long AUCTION_ID = 42L;
    private static final Long BUYER_ID = 1L;
    private static final Long SELLER_ID = 2L;
    private static final long FINAL_PRICE = 200_000L;

    @Mock
    private AuctionTradeRepository auctionTradeRepository;

    @Mock
    private DepositSettlementService depositSettlementService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Auction auction;

    @Mock
    private Member buyer;

    @Mock
    private Member seller;

    private TradeConfirmationService tradeConfirmationService;

    @BeforeEach
    void setUp() {
        tradeConfirmationService = new TradeConfirmationService(
                auctionTradeRepository,
                depositSettlementService,
                eventPublisher
        );
    }

    @Test
    void 구매자가_확정하면_최종가까지_추가_예치하고_판매자_확정_대기로_변경한다() {
        when(buyer.getId()).thenReturn(BUYER_ID);
        when(auction.getId()).thenReturn(AUCTION_ID);
        stubTrade(trade(TradeStatus.WAITING_CONFIRM));

        TradeConfirmationResult result = tradeConfirmationService.confirmBuyer(
                BUYER_ID,
                TRADE_ID
        );

        assertAll(
                () -> assertThat(result.tradeId()).isEqualTo(TRADE_ID),
                () -> assertThat(result.auctionId()).isEqualTo(AUCTION_ID),
                () -> assertThat(result.status()).isEqualTo(TradeStatus.CONFIRMED),
                () -> assertThat(result.finalPrice()).isEqualTo(FINAL_PRICE)
        );
        verify(depositSettlementService).topUpToFinalPrice(
                AUCTION_ID,
                BUYER_ID,
                FINAL_PRICE
        );
        verify(eventPublisher).publishEvent(new TradeStatusChanged(
                new TradeLiveState(TRADE_ID, AUCTION_ID, TradeStatus.CONFIRMED)
        ));
    }

    @Test
    void 판매자가_확정하면_대금을_전달하고_거래를_완료한다() {
        when(buyer.getId()).thenReturn(BUYER_ID);
        when(seller.getId()).thenReturn(SELLER_ID);
        when(auction.getId()).thenReturn(AUCTION_ID);
        when(auction.getSeller()).thenReturn(seller);
        stubTrade(trade(TradeStatus.CONFIRMED));

        TradeConfirmationResult result = tradeConfirmationService.confirmSeller(
                SELLER_ID,
                TRADE_ID
        );

        assertThat(result.status()).isEqualTo(TradeStatus.COMPLETED);
        verify(depositSettlementService).transferToSeller(
                AUCTION_ID,
                BUYER_ID,
                SELLER_ID,
                FINAL_PRICE
        );
        verify(eventPublisher).publishEvent(new TradeStatusChanged(
                new TradeLiveState(TRADE_ID, AUCTION_ID, TradeStatus.COMPLETED)
        ));
    }

    @Test
    void 거래_구매자가_아니면_구매자_확정을_거부한다() {
        when(buyer.getId()).thenReturn(BUYER_ID);
        stubTrade(trade(TradeStatus.WAITING_CONFIRM));

        TradeException exception = assertThrows(
                TradeException.class,
                () -> tradeConfirmationService.confirmBuyer(99L, TRADE_ID)
        );

        assertThat(exception.getErrorCode()).isEqualTo(TRADE_ACCESS_DENIED);
        verifyNoInteractions(depositSettlementService, eventPublisher);
    }

    @Test
    void 구매자_확정_전에는_판매자_확정을_거부한다() {
        when(seller.getId()).thenReturn(SELLER_ID);
        when(auction.getSeller()).thenReturn(seller);
        stubTrade(trade(TradeStatus.WAITING_CONFIRM));

        TradeException exception = assertThrows(
                TradeException.class,
                () -> tradeConfirmationService.confirmSeller(SELLER_ID, TRADE_ID)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(INVALID_TRADE_STATUS_TRANSITION);
        verifyNoInteractions(depositSettlementService, eventPublisher);
    }

    @Test
    void 존재하지_않는_거래의_확정을_거부한다() {
        when(auctionTradeRepository.findByIdForUpdate(TRADE_ID))
                .thenReturn(Optional.empty());

        TradeException exception = assertThrows(
                TradeException.class,
                () -> tradeConfirmationService.confirmBuyer(BUYER_ID, TRADE_ID)
        );

        assertThat(exception.getErrorCode()).isEqualTo(TRADE_NOT_FOUND);
        verifyNoInteractions(depositSettlementService, eventPublisher);
    }

    private AuctionTrade trade(TradeStatus status) {
        AuctionTrade trade = AuctionTrade.builder()
                .auction(auction)
                .buyer(buyer)
                .status(status)
                .finalPrice(FINAL_PRICE)
                .purchasedAt(LocalDateTime.of(2026, 8, 5, 12, 0))
                .build();
        ReflectionTestUtils.setField(trade, "id", TRADE_ID);
        return trade;
    }

    private void stubTrade(AuctionTrade trade) {
        when(auctionTradeRepository.findByIdForUpdate(TRADE_ID))
                .thenReturn(Optional.of(trade));
    }
}
