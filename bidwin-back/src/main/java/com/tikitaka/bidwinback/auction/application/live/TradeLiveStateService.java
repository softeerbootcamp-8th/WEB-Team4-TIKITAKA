package com.tikitaka.bidwinback.auction.application.live;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.exception.TradeException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionTradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.TRADE_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class TradeLiveStateService {

    private final AuctionTradeRepository auctionTradeRepository;

    /**
     * 커밋된 최신 상태를 별도 읽기 트랜잭션에서 조회해 롤백 상태가 새지 않게 한다.
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public TradeLiveState getState(long tradeId) {
        AuctionTrade trade = auctionTradeRepository.findById(tradeId)
                .orElseThrow(() -> new TradeException(TRADE_NOT_FOUND));
        return new TradeLiveState(
                trade.getId(),
                trade.getAuction().getId(),
                trade.getStatus()
        );
    }
}
