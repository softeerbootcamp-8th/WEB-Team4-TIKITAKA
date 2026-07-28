package com.tikitaka.bidwinback.auction.domain.entity;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.TradeType;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(name = "UpAuction")
@PrimaryKeyJoinColumn(name = "auction_id")
@NoArgsConstructor(access = PROTECTED)
@DiscriminatorValue("UP")
public class UpAuction extends Auction {

    @Column(name = "buy_now_price")
    private Long buyNowPrice;

    @Builder
    private UpAuction(
            Member seller,
            String title,
            String description,
            AuctionStatus status,
            AuctionCategory category,
            long startPrice,
            LocalDateTime endedAt,
            TradeType tradeType,
            String contact,
            Long buyNowPrice
    ) {
        super(
                seller,
                title,
                description,
                status,
                category,
                startPrice,
                endedAt,
                tradeType,
                contact
        );
        this.buyNowPrice = buyNowPrice;
    }
}
