package com.tikitaka.bidwinback.auction.domain.entity;

import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;
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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "Bid",
        indexes = @Index(
                name = "idx_bid_auction_id_price",
                columnList = "auction_id, price"
        )
)
@NoArgsConstructor(access = PROTECTED)
public class Bid extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bidder_id", nullable = false)
    private Member bidder;

    @Column(nullable = false)
    private long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BidStatus status;

    @Builder
    private Bid(Auction auction, Member bidder, long price, BidStatus status) {
        this.auction = auction;
        this.bidder = bidder;
        this.price = price;
        this.status = status;
    }
}
