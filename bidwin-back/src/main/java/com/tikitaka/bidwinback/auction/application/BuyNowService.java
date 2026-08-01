package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.exception.BidException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.CONCURRENT_TRADE_CONFLICT;

@Service
@RequiredArgsConstructor
public class BuyNowService {

    private final BuyNowTransactionService transactionService;

    public BuyNowResult buy(
            Long memberId,
            Long auctionId,
            String idempotencyKey
    ) {
        try {
            return transactionService.buy(memberId, auctionId, idempotencyKey);
        } catch (BidException exception) {
            if (exception.getErrorCode() != CONCURRENT_TRADE_CONFLICT) {
                throw exception;
            }
            return transactionService.replay(memberId, auctionId, idempotencyKey)
                    .orElseThrow(() -> exception);
        } catch (DataIntegrityViolationException exception) {
            return transactionService.replay(memberId, auctionId, idempotencyKey)
                    .orElseThrow(() -> exception);
        }
    }
}
