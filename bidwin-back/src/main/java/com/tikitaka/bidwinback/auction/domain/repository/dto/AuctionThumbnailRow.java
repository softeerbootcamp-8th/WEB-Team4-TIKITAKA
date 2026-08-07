package com.tikitaka.bidwinback.auction.domain.repository.dto;

/** 경매별 대표 썸네일(가장 먼저 등록된 이미지)의 objectKey. */
public record AuctionThumbnailRow(
        Long auctionId,
        String objectKey
) {
}
