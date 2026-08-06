package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AuctionTradeRepository extends JpaRepository<AuctionTrade, Long> {

    // 마이페이지 낙찰/구매 내역용. 두 탭이 같은 AuctionTrade를 상태 필터만 다르게 조회한다.
    @EntityGraph(attributePaths = "auction")
    Page<AuctionTrade> findByBuyerIdAndStatusIn(
            Long buyerId,
            List<TradeStatus> statuses,
            Pageable pageable
    );

    @Query("""
            select trade
            from AuctionTrade trade
            join fetch trade.buyer
            where trade.auction.id = :auctionId
            """)
    Optional<AuctionTrade> findByAuctionId(@Param("auctionId") long auctionId);

    @Query("""
            select trade.finalPrice
            from AuctionTrade trade
            where trade.auction.id = :auctionId
            """)
    Optional<Long> findFinalPriceByAuctionId(@Param("auctionId") long auctionId);

    long countByAuctionSellerIdAndStatus(long sellerId, TradeStatus status);
}
