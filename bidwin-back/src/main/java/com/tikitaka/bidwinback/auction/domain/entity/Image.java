package com.tikitaka.bidwinback.auction.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(name = "Image")
@NoArgsConstructor(access = PROTECTED)
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    // 같은 objectKey가 서로 다른 두 경매에 동시에 붙는 걸 DB 차원에서 막는다
    // (경매 등록 요청이 중복 도착해도 하나만 성공하도록 하는 마지막 방어선).
    // 발급 단계의 pending image와 같은 길이를 써야 utf8mb4에서도 UNIQUE 인덱스 한도를 넘지 않는다.
    @Column(name = "object_key", nullable = false, length = 100, unique = true)
    private String objectKey;

    @Builder
    private Image(Auction auction, String objectKey) {
        this.auction = auction;
        this.objectKey = objectKey;
    }
}
