package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImageRepository extends JpaRepository<Image, Long> {

    List<Image> findByAuctionIdOrderByIdAsc(long auctionId);

    // 목록 조회에서 카드마다 대표 이미지(맨 처음 등록한 것) 하나만 필요할 때 일괄로 가져온다.
    List<Image> findByAuctionIdInOrderByIdAsc(List<Long> auctionIds);
}
