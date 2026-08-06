package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.Image;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionThumbnailRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ImageRepository extends JpaRepository<Image, Long> {

    List<Image> findByAuctionIdOrderByIdAsc(long auctionId);

    // 여러 경매의 대표 썸네일을 한 번에 조회한다(마이페이지 목록 N+1 방지).
    // 경매마다 가장 먼저 등록된 이미지(최소 id) 한 장만 가져온다.
    @Query("""
            select image.auction.id, image.objectKey
            from Image image
            where image.auction.id in :auctionIds
              and image.id = (
                  select min(other.id)
                  from Image other
                  where other.auction.id = image.auction.id
              )
            """)
    List<AuctionThumbnailRow> findRepresentativeThumbnails(
            @Param("auctionIds") Collection<Long> auctionIds
    );
}
