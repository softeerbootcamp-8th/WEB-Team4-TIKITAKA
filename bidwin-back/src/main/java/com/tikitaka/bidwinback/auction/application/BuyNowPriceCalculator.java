package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.DownAuctionCurrentPriceCalculator;
import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.exception.BidException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

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
        // 목록 Top-K와 구매 확정가가 같은 시각·같은 가격 공식을 사용해야
        // 하락 주기 경계에서도 정렬된 가격과 실제 구매가가 어긋나지 않는다.
        return DownAuctionCurrentPriceCalculator.calculate(
                auction.getStartPrice(),
                auction.getMinimumPrice(),
                auction.getDropPrice(),
                auction.getPriceDropInterval(),
                auction.getStartedAt(),
                purchasedAt
        );
    }
}
