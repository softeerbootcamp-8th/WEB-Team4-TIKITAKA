package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionFinalPrice;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    // 거래 상세·SSE 조회용. 잠금 없이 역할 판별과 연락처 게이팅에 필요한 연관을 함께 가져온다.
    @Query("""
            select trade
            from AuctionTrade trade
            join fetch trade.buyer
            join fetch trade.auction auction
            join fetch auction.seller
            where trade.id = :tradeId
            """)
    Optional<AuctionTrade> findDetailById(@Param("tradeId") Long tradeId);

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
