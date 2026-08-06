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
           
    // 목록 조회에서 카드마다 대표 이미지(맨 처음 등록한 것) 하나만 필요하다.
    // auctionId별 최소 id(=가장 먼저 등록한 사진)만 서브쿼리로 골라서, 안 쓰는 나머지
    // 이미지 행은 DB에서부터 아예 가져오지 않는다.
    @Query("""
            select image from Image image
            where image.id in (
                select min(other.id) from Image other
                where other.auction.id in :auctionIds
                group by other.auction.id
            )
            """)
    List<Image> findFirstImageByAuctionIds(@Param("auctionIds") List<Long> auctionIds);
}
