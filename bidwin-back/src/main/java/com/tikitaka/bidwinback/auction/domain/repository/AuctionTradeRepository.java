package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
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

    long countByAuctionSellerIdAndStatus(long sellerId, TradeStatus status);

    // 마이페이지 구매 물품: 내가 산 거래. 경매를 함께 가져와 제목·시작가·유형을 매핑한다.
    @Query("""
            select trade
            from AuctionTrade trade
            join fetch trade.auction
            where trade.buyer.id = :memberId
              and trade.status in :statuses
            order by trade.id desc
            limit 3
            """)
    List<AuctionTrade> findBuyingItems(
            @Param("memberId") long memberId,
            @Param("statuses") Collection<TradeStatus> statuses
    );

    // 마이페이지 진행 중 거래 배너: 구매자는 확인 대기, 판매자는 확인 완료인 거래를 조회한다.
    // 역할 판별을 위해 경매·판매자·구매자를 함께 가져온다.
    @Query("""
            select trade
            from AuctionTrade trade
            join fetch trade.auction auction
            join fetch auction.seller
            join fetch trade.buyer
            where (trade.buyer.id = :memberId and trade.status = :buyerStatus)
               or (auction.seller.id = :memberId and trade.status = :sellerStatus)
            order by trade.purchasedAt asc, trade.id asc
            """)
    List<AuctionTrade> findActiveTrades(
            @Param("memberId") long memberId,
            @Param("buyerStatus") TradeStatus buyerStatus,
            @Param("sellerStatus") TradeStatus sellerStatus
    );

}
