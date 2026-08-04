package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    @EntityGraph(attributePaths = "seller")
    @Query("select auction from Auction auction where auction.id = :auctionId")
    Optional<Auction> findDetailById(@Param("auctionId") long auctionId);

    // 목록 조회용. 정렬·타입 필터·현재가 계산은 서비스에서 처리하고(1차 뼈대라 원시적으로),
    // 여기서는 키워드로만 좁힌 전체 목록을 가져온다.
    @EntityGraph(attributePaths = "seller")
    @Query("""
            select auction from Auction auction
            where (:keyword is null or lower(auction.title) like lower(concat('%', :keyword, '%')))
            """)
    List<Auction> findAllForList(@Param("keyword") String keyword);

    // 하락 경매의 계산 기준이 애플리케이션 서버마다 달라지지 않도록 DB 시각을 사용한다.
    @Query(value = "select current_timestamp(6)", nativeQuery = true)
    LocalDateTime currentDatabaseTime();
}
