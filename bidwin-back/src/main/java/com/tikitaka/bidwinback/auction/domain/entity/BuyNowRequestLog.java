package com.tikitaka.bidwinback.auction.domain.entity;

import com.tikitaka.bidwinback.global.common.entity.BaseTimeEntity;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "buy_now_request_log",
        // 요구사항: 멱등 키 하나는 하나의 완료된 구매 요청만 식별해야 한다.
        uniqueConstraints = @UniqueConstraint(
                name = BuyNowRequestLog.IDEMPOTENCY_KEY_UNIQUE_CONSTRAINT,
                columnNames = "idempotency_key"
        )
)
@NoArgsConstructor(access = PROTECTED)
public class BuyNowRequestLog extends BaseTimeEntity {

    public static final String IDEMPOTENCY_KEY_UNIQUE_CONSTRAINT =
            "uk_buy_now_request_log_idempotency_key";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auction_id", nullable = false, updatable = false)
    private Auction auction;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trade_id", nullable = false, updatable = false, unique = true)
    private AuctionTrade trade;

    private BuyNowRequestLog(
            String idempotencyKey,
            Member member,
            Auction auction,
            AuctionTrade trade
    ) {
        this.idempotencyKey = idempotencyKey;
        this.member = member;
        this.auction = auction;
        this.trade = trade;
    }

    public static BuyNowRequestLog completed(
            String idempotencyKey,
            Member member,
            Auction auction,
            AuctionTrade trade
    ) {
        return new BuyNowRequestLog(idempotencyKey, member, auction, trade);
    }

    public boolean matches(Long memberId, Long auctionId) {
        return member.getId().equals(memberId) && auction.getId().equals(auctionId);
    }
}
