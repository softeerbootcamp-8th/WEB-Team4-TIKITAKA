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
@Table(name = "DownAuction")
@NoArgsConstructor(access = PROTECTED)
public class DownAuction {

    @Id
    @Column(name = "auction_id")
    private Long auctionId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @Column(name = "minimum_price", nullable = false)
    private long minimumPrice;

    @Column(name = "drop_price", nullable = false)
    private long dropPrice;

    @Column(name = "price_drop_interval", nullable = false)
    private long priceDropInterval;

    @Builder
    private DownAuction(
            Auction auction,
            long minimumPrice,
            long dropPrice,
            long priceDropInterval
    ) {
        this.auction = auction;
        this.minimumPrice = minimumPrice;
        this.dropPrice = dropPrice;
        this.priceDropInterval = priceDropInterval;
    }
}
