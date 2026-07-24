package com.tikitaka.bidwinback.auction.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(name = "UpAuction")
@NoArgsConstructor(access = PROTECTED)
public class UpAuction {

    @Id
    @Column(name = "auction_id")
    private Long auctionId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @Column(name = "buy_now_price")
    private Long buyNowPrice;

    @Builder
    private UpAuction(Auction auction, Long buyNowPrice) {
        this.auction = auction;
        this.buyNowPrice = buyNowPrice;
    }
}
