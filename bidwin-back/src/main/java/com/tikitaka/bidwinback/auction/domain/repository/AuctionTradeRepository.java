package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionFinalPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuctionTradeRepository extends JpaRepository<AuctionTrade, Long> {

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

    // 실시간 상태 스냅샷용 일괄 조회. 완료된 경매의 확정 거래가를 목록 SSE에서 한 번에 모은다.
    @Query("""
            select trade.auction.id, trade.finalPrice
            from AuctionTrade trade
            where trade.auction.id in :auctionIds
            """)
    List<AuctionFinalPrice> findFinalPricesByAuctionIds(
            @Param("auctionIds") Collection<Long> auctionIds
    );

    long countByAuctionSellerIdAndStatus(long sellerId, TradeStatus status);
}
