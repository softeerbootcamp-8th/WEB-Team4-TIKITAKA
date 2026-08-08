package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.application.live.TradeStatusChanged;
import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.exception.TradeException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionTradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.TRADE_ACCESS_DENIED;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.TRADE_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class TradeConfirmationService {

    private final AuctionTradeRepository auctionTradeRepository;
    private final DepositSettlementService depositSettlementService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public TradeConfirmationResult confirmBuyer(Long memberId, Long tradeId) {
        AuctionTrade trade = findTradeForUpdate(tradeId);
        validateBuyer(trade, memberId);
        trade.confirmBuyer();

        depositSettlementService.topUpToFinalPrice(
                trade.getAuction().getId(),
                trade.getBuyer().getId(),
                trade.getFinalPrice()
        );
        // 커밋 뒤 거래 SSE로 상태 변경(WAITING_CONFIRM→CONFIRMED)을 전파해 양쪽 화면을 갱신한다.
        eventPublisher.publishEvent(new TradeStatusChanged(trade.getId()));
        return TradeConfirmationResult.from(trade);
    }

    @Transactional
    public TradeConfirmationResult confirmSeller(Long memberId, Long tradeId) {
        AuctionTrade trade = findTradeForUpdate(tradeId);
        validateSeller(trade, memberId);
        trade.confirmSeller();

        depositSettlementService.transferToSeller(
                trade.getAuction().getId(),
                trade.getBuyer().getId(),
                trade.getAuction().getSeller().getId(),
                trade.getFinalPrice()
        );
        // 커밋 뒤 거래 SSE로 상태 변경(CONFIRMED→COMPLETED)을 전파해 양쪽 화면을 갱신한다.
        eventPublisher.publishEvent(new TradeStatusChanged(trade.getId()));
        return TradeConfirmationResult.from(trade);
    }

    private AuctionTrade findTradeForUpdate(Long tradeId) {
        return auctionTradeRepository.findByIdForUpdate(tradeId)
                .orElseThrow(() -> new TradeException(TRADE_NOT_FOUND));
    }

    private void validateBuyer(AuctionTrade trade, Long memberId) {
        if (!trade.getBuyer().getId().equals(memberId)) {
            throw new TradeException(TRADE_ACCESS_DENIED);
        }
    }

    private void validateSeller(AuctionTrade trade, Long memberId) {
        if (!trade.getAuction().getSeller().getId().equals(memberId)) {
            throw new TradeException(TRADE_ACCESS_DENIED);
        }
    }
}
