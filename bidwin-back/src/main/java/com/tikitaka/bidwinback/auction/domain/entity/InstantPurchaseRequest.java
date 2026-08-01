package com.tikitaka.bidwinback.auction.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "instant_purchase_request",
        uniqueConstraints = @UniqueConstraint(
                name = InstantPurchaseRequest.IDEMPOTENCY_KEY_UNIQUE_CONSTRAINT,
                columnNames = "idempotency_key"
        )
)
@NoArgsConstructor(access = PROTECTED)
public class InstantPurchaseRequest {

    public static final String IDEMPOTENCY_KEY_UNIQUE_CONSTRAINT =
            "uk_instant_purchase_request_idempotency_key";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 100,
            columnDefinition = "varchar(100) CHARACTER SET ascii COLLATE ascii_bin"
    )
    private String idempotencyKey;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "auction_id", nullable = false)
    private Long auctionId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_id", unique = true)
    private AuctionTrade trade;

    @Column(name = "final_price")
    private Long finalPrice;

    public boolean belongsTo(Long buyerId, Long auctionId) {
        return this.buyerId.equals(buyerId) && this.auctionId.equals(auctionId);
    }

    public boolean isCompleted() {
        return trade != null && finalPrice != null;
    }

    public void complete(AuctionTrade completedTrade, long completedPrice) {
        this.trade = completedTrade;
        this.finalPrice = completedPrice;
    }
}
