package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuctionTradeRepository extends JpaRepository<AuctionTrade, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select trade
            from AuctionTrade trade
            join fetch trade.buyer
            join fetch trade.auction auction
            join fetch auction.seller
            where trade.id = :tradeId
            """)
    Optional<AuctionTrade> findByIdForUpdate(@Param("tradeId") Long tradeId);

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
