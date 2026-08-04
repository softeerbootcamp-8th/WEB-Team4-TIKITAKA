package com.tikitaka.bidwinback.auction.domain.entity;

import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
import com.tikitaka.bidwinback.global.common.entity.BaseTimeEntity;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "Auction_trade",
        // 요구사항: 하나의 경매에는 최종 거래가 하나만 존재해야 한다.
        uniqueConstraints = @UniqueConstraint(
                name = AuctionTrade.AUCTION_UNIQUE_CONSTRAINT,
                columnNames = "auction_id"
        )
)
@NoArgsConstructor(access = PROTECTED)
public class AuctionTrade extends BaseTimeEntity {

    public static final String AUCTION_UNIQUE_CONSTRAINT =
            "uk_auction_trade_auction";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private Member buyer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeStatus status;

    @Column(name = "final_price", nullable = false)
    private long finalPrice;

    @Column(name = "purchased_at", nullable = false, updatable = false)
    private LocalDateTime purchasedAt;

    @Builder
    private AuctionTrade(
            Auction auction,
            Member buyer,
            TradeStatus status,
            long finalPrice,
            LocalDateTime purchasedAt
    ) {
        this.auction = auction;
        this.buyer = buyer;
        this.status = status == null ? TradeStatus.WAITING_CONFIRM : status;
        this.finalPrice = finalPrice;
        this.purchasedAt = purchasedAt;
    }
}
