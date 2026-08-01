package com.tikitaka.bidwinback.auction.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuyNowService {

    private final BuyNowTransactionService transactionService;

    public BuyNowResult buy(
            Long memberId,
            Long auctionId,
            String idempotencyKey
    ) {
        return transactionService.buy(memberId, auctionId, idempotencyKey);
    }
}
