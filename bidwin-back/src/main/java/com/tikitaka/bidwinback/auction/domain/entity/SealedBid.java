package com.tikitaka.bidwinback.auction.domain.entity;

import com.tikitaka.bidwinback.member.domain.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;

import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "sealed_bid",
        uniqueConstraints = @UniqueConstraint(
                name = SealedBid.AUCTION_BIDDER_UNIQUE_CONSTRAINT,
                columnNames = {"auction_id", "bidder_id"}
        ),
        indexes = @Index(
                name = "idx_sealed_bid_auction_price",
                columnList = "auction_id, price"
        )
)
@NoArgsConstructor(access = PROTECTED)
public class SealedBid {

    public static final String AUCTION_BIDDER_UNIQUE_CONSTRAINT =
            "uk_sealed_bid_auction_bidder";

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

    @CreationTimestamp(source = SourceType.DB)
    @Column(
            name = "submitted_at",
            nullable = false,
            updatable = false,
            columnDefinition = "datetime(6) default current_timestamp(6)"
    )
    private LocalDateTime submittedAt;

    @Builder
    private SealedBid(Auction auction, Member bidder, long price) {
        this.auction = auction;
        this.bidder = bidder;
        this.price = price;
    }
}
