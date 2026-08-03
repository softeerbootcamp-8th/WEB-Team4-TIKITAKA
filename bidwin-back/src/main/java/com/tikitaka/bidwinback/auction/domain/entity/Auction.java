package com.tikitaka.bidwinback.auction.domain.entity;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.TradeType;
import com.tikitaka.bidwinback.global.common.entity.BaseTimeEntity;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;

import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(name = "Auction")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "auction_type")
@NoArgsConstructor(access = PROTECTED)
public abstract class Auction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Member seller;

    @Column(nullable = false, length = 30)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuctionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuctionCategory category;

    @Column(name = "start_price", nullable = false)
    private long startPrice;

    @Column(name = "ended_at", nullable = false)
    private LocalDateTime endedAt;

    @CreationTimestamp(source = SourceType.DB)
    @Column(
            name = "started_at",
            nullable = false,
            updatable = false,
            columnDefinition = "datetime(6) default current_timestamp(6)"
    )
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false)
    private TradeType tradeType;

    @Column(nullable = false, length = 100)
    private String contact;

    protected Auction(
            Member seller,
            String title,
            String description,
            AuctionStatus status,
            AuctionCategory category,
            long startPrice,
            LocalDateTime endedAt,
            TradeType tradeType,
            String contact
    ) {
        this.seller = seller;
        this.title = title;
        this.description = description;
        this.status = status == null ? AuctionStatus.OPEN : status;
        this.category = category;
        this.startPrice = startPrice;
        this.endedAt = endedAt;
        this.tradeType = tradeType;
        this.contact = contact;
    }
}
