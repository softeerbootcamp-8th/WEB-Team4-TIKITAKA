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
@Table(name = "DownAuction")
@PrimaryKeyJoinColumn(name = "auction_id")
@NoArgsConstructor(access = PROTECTED)
@DiscriminatorValue("DOWN")
public class DownAuction extends Auction {

    @Column(name = "minimum_price", nullable = false)
    private long minimumPrice;

    @Column(name = "drop_price", nullable = false)
    private long dropPrice;

    @Column(name = "price_drop_interval", nullable = false)
    // 하락 주기의 저장 단위는 분이다.
    private long priceDropInterval;

    @Builder
    private DownAuction(
            Member seller,
            String title,
            String description,
            AuctionStatus status,
            AuctionCategory category,
            long startPrice,
            LocalDateTime endedAt,
            TradeType tradeType,
            String contact,
            long minimumPrice,
            long dropPrice,
            long priceDropInterval
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
        this.minimumPrice = minimumPrice;
        this.dropPrice = dropPrice;
        this.priceDropInterval = priceDropInterval;
    }
}
