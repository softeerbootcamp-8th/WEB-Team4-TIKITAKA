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
            // 요구사항: 동일 멱등 키의 동시 요청은 선행 요청이 만든 거래 결과를 반환한다.
            return transactionService.replay(memberId, auctionId, idempotencyKey)
                    .orElseThrow(() -> exception);
        } catch (DataIntegrityViolationException exception) {
            // 요구사항: 멱등 로그 UNIQUE 경합 시에도 완료된 기존 거래를 재조회한다.
            return transactionService.replay(memberId, auctionId, idempotencyKey)
                    .orElseThrow(() -> exception);
        }
    }
}
