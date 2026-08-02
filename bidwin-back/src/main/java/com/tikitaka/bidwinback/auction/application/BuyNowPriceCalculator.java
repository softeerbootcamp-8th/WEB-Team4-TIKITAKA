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
        // 요구사항: 상향 경매의 최종가는 판매자가 설정한 즉시구매가로 확정한다.
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

        // 요구사항: 하향 경매는 완료 시각까지 지난 하락 주기만큼 가격을 내린다.
        long elapsedMinutes = Math.max(
                0,
                ChronoUnit.MINUTES.between(auction.getStartedAt(), purchasedAt)
        );
        long elapsedDrops = elapsedMinutes / auction.getPriceDropInterval();
        long priceRange = auction.getStartPrice() - auction.getMinimumPrice();
        long dropsBeforeFloor = priceRange / auction.getDropPrice();

        // 요구사항: 하향 경매의 최종가는 설정된 최저가보다 낮아질 수 없다.
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
