package com.tikitaka.bidwinback.auction.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(name = "down_price_snapshot")
@IdClass(DownPriceSnapshotId.class)
@NoArgsConstructor(access = PROTECTED)
public class DownPriceSnapshot {

    @Id
    @Column(name = "snapshot_at", nullable = false)
    private LocalDateTime snapshotAt;

    @Id
    @Column(name = "auction_id", nullable = false)
    private Long auctionId;

    @Column(name = "price", nullable = false)
    private Long price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", insertable = false, updatable = false)
    private Auction auction;
}
