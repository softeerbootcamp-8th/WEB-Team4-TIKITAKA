package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.exception.BidException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.BUY_NOW_PRICE_NOT_SET;

@Component
public class BuyNowPriceCalculator {

    public long calculate(Auction auction, LocalDateTime purchasedAt) {
        if (auction instanceof UpAuction upAuction) {
            return calculateUpAuctionPrice(upAuction);
        }
        if (auction instanceof DownAuction downAuction) {
            return calculateDownAuctionPrice(downAuction, purchasedAt);
        }
        throw new IllegalStateException("지원하지 않는 경매 유형입니다.");
    }

    private long calculateUpAuctionPrice(UpAuction auction) {
        Long buyNowPrice = auction.getBuyNowPrice();
        if (buyNowPrice == null) {
            throw new BidException(BUY_NOW_PRICE_NOT_SET);
        }
        return buyNowPrice;
    }

    private long calculateDownAuctionPrice(
            DownAuction auction,
            LocalDateTime purchasedAt
    ) {
        validateDownAuctionPricing(auction);

        long elapsedMinutes = Math.max(
                0,
                ChronoUnit.MINUTES.between(auction.getStartedAt(), purchasedAt)
        );
        long elapsedDrops = elapsedMinutes / auction.getPriceDropInterval();
        long priceRange = auction.getStartPrice() - auction.getMinimumPrice();
        long dropsBeforeFloor = priceRange / auction.getDropPrice();

        if (elapsedDrops > dropsBeforeFloor) {
            return auction.getMinimumPrice();
        }
        return auction.getStartPrice() - elapsedDrops * auction.getDropPrice();
    }

    private void validateDownAuctionPricing(DownAuction auction) {
        if (auction.getStartedAt() == null
                || auction.getPriceDropInterval() <= 0
                || auction.getDropPrice() <= 0
                || auction.getMinimumPrice() < 0
                || auction.getMinimumPrice() > auction.getStartPrice()) {
            throw new IllegalStateException("하향 경매 가격 설정이 올바르지 않습니다.");
        }
    }
}
