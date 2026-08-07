package com.tikitaka.bidwinback.auction.domain.repository.dto;

import java.time.LocalDateTime;

/** 특정 회원이 특정 경매에 넣은 입찰들 중 최고가와 마지막 입찰 시각. */
public record MyBidAggregate(
        Long auctionId,
        Long myHighestPrice,
        LocalDateTime myLastBidAt
) {
}
