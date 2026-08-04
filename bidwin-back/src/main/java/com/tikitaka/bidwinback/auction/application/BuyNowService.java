package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.tikitaka.bidwinback.auction.domain.enums.BidStatus.BUY_NOW;
import static com.tikitaka.bidwinback.auction.domain.enums.BidStatus.DOWN;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class BuyNowService {

    private final AuctionRepository auctionRepository;
    private final BuyNowPriceCalculator priceCalculator;
    private final BuyNowTransactionService transactionService;

    public BuyNowResult buyUpAuction(
            Long memberId,
            Long auctionId,
            String idempotencyKey
    ) {
        UpAuction auction = findAuction(auctionId, UpAuction.class);
        LocalDateTime purchasedAt = auctionRepository.currentDatabaseTime();
        long finalPrice = priceCalculator.calculate(auction, purchasedAt);

        return transactionService.buy(new BuyNowCommand(
                memberId,
                auctionId,
                idempotencyKey,
                finalPrice,
                purchasedAt,
                BUY_NOW
        ));
    }

    public BuyNowResult buyDownAuction(
            Long memberId,
            Long auctionId,
            String idempotencyKey
    ) {
        DownAuction auction = findAuction(auctionId, DownAuction.class);
        LocalDateTime purchasedAt = auctionRepository.currentDatabaseTime();
        long finalPrice = priceCalculator.calculate(auction, purchasedAt);

        return transactionService.buy(new BuyNowCommand(
                memberId,
                auctionId,
                idempotencyKey,
                finalPrice,
                purchasedAt,
                DOWN
        ));
    }

    private <T extends Auction> T findAuction(
            Long auctionId,
            Class<T> auctionType
    ) {
        return auctionRepository.findById(auctionId)
                .filter(auctionType::isInstance)
                .map(auctionType::cast)
                .orElseThrow(() -> new AuctionException(AUCTION_NOT_FOUND));
    }
}
