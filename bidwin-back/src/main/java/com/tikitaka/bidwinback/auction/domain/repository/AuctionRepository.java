package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    // 매초 실행되는 조회가 이미 종료된 경매 전체를 훑지 않도록 상태와 DB 시각으로 후보만 찾는다.
    @Query(value = """
            SELECT id
            FROM auction
            WHERE status IN ('OPEN', 'BID_ONGOING')
              AND ended_at <= SYSDATE(6)
            ORDER BY ended_at, id
            """, nativeQuery = true)
    List<Long> findClosingCandidateIds();

    // 후보 조회 뒤 상태가 바뀔 수 있으므로 현재 조건을 다시 검사하며 한 행만 선점한다.
    // 다른 트랜잭션이 입찰·즉시구매·마감을 진행 중이면 기다리지 않고 다음 주기에 재시도한다.
    @Query(value = """
            SELECT id
            FROM auction
            WHERE id = :auctionId
              AND status IN ('OPEN', 'BID_ONGOING')
              AND ended_at <= SYSDATE(6)
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<Long> findClosingCandidateIdForUpdateSkipLocked(
            @Param("auctionId") long auctionId
    );

    // 단일 조건부 UPDATE로 입찰을 직렬화하고 최소 호가 검증과 현재가 변경을 원자적으로 처리한다.
    // current_price가 없는 기존 경매만 Bid 최고가, 입찰도 없으면 시작가를 기준으로 한다.
    // 락 대기 중 흐른 시간까지 반영하도록 statement 시작 시각이 아닌 SYSDATE(6)를 사용한다.
    @Modifying
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "3000"))
    @Query(value = """
            UPDATE auction
            SET current_price = :price,
                status = 'BID_ONGOING',
                last_modified_at = SYSDATE(6) -- Native UPDATE는 @LastModifiedDate가 적용되지 않아 직접 갱신한다.
            WHERE id = :auctionId
              AND auction_type = 'UP'
              AND status IN ('OPEN', 'BID_ONGOING')
              AND completed_at IS NULL
              AND ended_at > DATE_ADD(SYSDATE(6), INTERVAL 5 MINUTE)
              AND seller_id <> :bidderId
              AND COALESCE(
                    current_price,
                    (
                        SELECT MAX(bid.price)
                        FROM bid
                        WHERE bid.auction_id = auction.id
                    ),
                    start_price
              ) <= :price - :bidUnit
            """, nativeQuery = true)
    int updateCurrentPriceForBid(
            @Param("auctionId") Long auctionId,
            @Param("bidderId") Long bidderId,
            @Param("price") long price,
            @Param("bidUnit") long bidUnit
    );

    // 밀봉 구간에는 공개 현재가를 바꾸지 않고 시작가·일반·밀봉 최고가보다 높은 입찰만 허용한다.
    @Modifying
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "3000"))
    @Query(value = """
            UPDATE auction
            SET status = 'BID_ONGOING',
                last_modified_at = SYSDATE(6)
            WHERE id = :auctionId
              AND auction_type = 'UP'
              AND status IN ('OPEN', 'BID_ONGOING')
              AND completed_at IS NULL
              AND ended_at > SYSDATE(6)
              AND ended_at <= DATE_ADD(SYSDATE(6), INTERVAL 5 MINUTE)
              AND seller_id <> :bidderId
              AND GREATEST(
                    COALESCE(
                          current_price,
                          (
                              SELECT MAX(bid.price)
                              FROM bid
                              WHERE bid.auction_id = auction.id
                          ),
                          start_price
                    ),
                    COALESCE(
                          (
                              SELECT MAX(sealed_bid.price)
                              FROM sealed_bid
                              WHERE sealed_bid.auction_id = auction.id
                          ),
                          start_price
                    )
              ) <= :price - :bidUnit
            """, nativeQuery = true)
    int tryUpdateAuctionForSealedBid(
            @Param("auctionId") Long auctionId,
            @Param("bidderId") Long bidderId,
            @Param("price") long price,
            @Param("bidUnit") long bidUnit
    );

    // 정산 시 진행 중인 입찰과 중복 정산을 동일 경매 행 기준으로 직렬화한다.
    // 정산에서 사용하지 않는 판매자까지 잠그지 않도록 fetch join은 하지 않는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select auction
            from Auction auction
            where auction.id = :auctionId
            """)
    Optional<Auction> findByIdForUpdate(@Param("auctionId") long auctionId);

    @Query("""
            select auction
            from Auction auction
            join fetch auction.seller
            where auction.id = :auctionId
            """)
    Optional<Auction> findWithSellerById(@Param("auctionId") Long auctionId);

    // 경매 상태·마감·판매자를 DB에서 다시 검사하고 한 요청만 완료 처리한다.
    @Modifying
    @Query(value = """
            UPDATE auction
            SET status = 'COMPLETED',
                completed_at = :completedAt,
                last_modified_at = :completedAt
            WHERE id = :auctionId
              AND status = 'OPEN'
              AND completed_at IS NULL
              AND ended_at > SYSDATE(6)
              AND (
                    auction_type = 'DOWN'
                    OR ended_at > DATE_ADD(SYSDATE(6), INTERVAL 5 MINUTE)
              )
              AND seller_id <> :buyerId
            """, nativeQuery = true)
    int completeForBuyNow(
            @Param("auctionId") Long auctionId,
            @Param("buyerId") Long buyerId,
            @Param("completedAt") LocalDateTime completedAt
    );

    // 요구사항: 낙찰 시각과 하향 경매 가격 계산은 DB가 기록한 동일 시각을 사용한다.
    @Query(value = """
            SELECT completed_at
            FROM auction
            WHERE id = :auctionId
            """, nativeQuery = true)
    Optional<LocalDateTime> findCompletedAt(@Param("auctionId") Long auctionId);

    @EntityGraph(attributePaths = "seller")
    @Query("select auction from Auction auction where auction.id = :auctionId")
    Optional<Auction> findDetailById(@Param("auctionId") long auctionId);

    // 목록 조회용. 정렬·타입 필터·현재가 계산은 서비스에서 처리하고(1차 뼈대라 원시적으로),
    // 여기서는 키워드로 좁히고 "asOf 시점에 활성 상태인" 경매만 가져온다.
    // createdAt <= asOf까지 걸어야, asOf 스냅샷을 공유하는 다음 페이지 요청 사이에 새로
    // 등록된 경매가 끼어들어 목록 구성이 흔들리는 걸 막을 수 있다.
    // completedAt은 "지금 null이냐"가 아니라 "asOf 시점엔 완료 전이었냐"로 봐야 한다.
    // 그래야 asOf를 공유하는 페이지 요청 사이에 즉시구매가 체결돼도, 그 뒤에 있던 다른 경매가
    // 목록 인덱스가 밀리면서 통째로 스킵되는 일이 없다. completedAt is null과 completedAt > asOf를
    // OR로 묶을 땐 반드시 괄호로 감싸야 한다 — AND가 OR보다 우선순위가 높아서, 괄호 없이 쓰면
    // "이 OR 조건만 참이면 keyword/createdAt 조건까지 전부 무시하고 통과"하는 전혀 다른 쿼리가 된다.
    @EntityGraph(attributePaths = "seller")
    @Query("""
            select auction from Auction auction
            where (:keyword is null or lower(auction.title) like lower(concat('%', :keyword, '%')))
              and auction.createdAt <= :asOf
              and (auction.completedAt is null or auction.completedAt > :asOf)
              and auction.endedAt > :asOf
            """)
    List<Auction> findAllForList(
            @Param("keyword") String keyword,
            @Param("asOf") LocalDateTime asOf
    );

    // 하락 경매의 계산 기준이 애플리케이션 서버마다 달라지지 않도록 DB 시각을 사용한다.
    @Query(value = "select current_timestamp(6)", nativeQuery = true)
    LocalDateTime currentDatabaseTime();

    // 마이페이지 판매 물품: 내가 올린 경매를 최신순으로. 유형(UP/DOWN)은 서비스에서 구체 타입으로 매핑한다.
    List<Auction> findTop3BySellerIdOrderByIdDesc(long sellerId);
}
