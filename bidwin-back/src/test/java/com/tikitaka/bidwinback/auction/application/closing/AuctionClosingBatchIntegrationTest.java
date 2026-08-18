package com.tikitaka.bidwinback.auction.application.closing;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionDeposit;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.DepositStatus;
import com.tikitaka.bidwinback.auction.domain.enums.TradeType;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = "app.storage.s3.bucket=test-bucket")
@Transactional
class AuctionClosingBatchIntegrationTest {

    private static final long BID_UNIT = 1_000L;
    private static final long START_PRICE = 100_000L;
    private static final int BATCH_SIZE = 200;

    @Autowired
    private AuctionClosingService auctionClosingService;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 공개_최고가_입찰자가_낙찰되고_거래가_생성된다() {
        // given
        Member seller = persistMember("batch-open-seller");
        Member loser = persistMember("batch-open-loser");
        Member winner = persistMember("batch-open-winner");
        UpAuction auction = persistAuction(seller);
        placePublicBid(auction.getId(), loser.getId(), START_PRICE + BID_UNIT);
        placePublicBid(auction.getId(), winner.getId(), START_PRICE + (BID_UNIT * 3));
        long revisionBeforeClosing = revisionOf(auction.getId());
        endNow(auction.getId());

        // when
        auctionClosingService.closeBatch(AuctionStatus.BID_ONGOING, BATCH_SIZE);

        // then
        List<Object[]> trades = tradesOf(auction.getId());
        assertAll(
                () -> assertThat(statusOf(auction.getId())).isEqualTo("COMPLETED"),
                () -> assertThat(columnOf(auction.getId(), "current_price"))
                        .isEqualTo(START_PRICE + (BID_UNIT * 3)),
                () -> assertThat(columnOf(auction.getId(), "completed_at")).isNotNull(),
                () -> assertThat(revisionOf(auction.getId()))
                        .isEqualTo(revisionBeforeClosing + 1),
                () -> assertThat(trades).hasSize(1),
                () -> assertThat(((Number) trades.get(0)[0]).longValue())
                        .isEqualTo(winner.getId()),
                () -> assertThat(((Number) trades.get(0)[1]).longValue())
                        .isEqualTo(START_PRICE + (BID_UNIT * 3)),
                () -> assertThat(trades.get(0)[2]).isEqualTo("WAITING_CONFIRM")
        );
    }

    @Test
    void 밀봉_최고가가_더_높으면_밀봉입찰자가_낙찰된다() {
        // given
        Member seller = persistMember("batch-sealed-seller");
        Member publicBidder = persistMember("batch-sealed-public");
        Member sealedBidder = persistMember("batch-sealed-winner");
        UpAuction auction = persistAuction(seller);
        placePublicBid(auction.getId(), publicBidder.getId(), START_PRICE + (BID_UNIT * 2));
        placeSealedBid(auction.getId(), sealedBidder.getId(), START_PRICE + (BID_UNIT * 9));
        endNow(auction.getId());

        // when
        auctionClosingService.closeBatch(AuctionStatus.BID_ONGOING, BATCH_SIZE);

        // then
        List<Object[]> trades = tradesOf(auction.getId());
        assertAll(
                () -> assertThat(statusOf(auction.getId())).isEqualTo("COMPLETED"),
                () -> assertThat(columnOf(auction.getId(), "current_price"))
                        .isEqualTo(START_PRICE + (BID_UNIT * 9)),
                // bid_count는 공개입찰 전용이라 마감이 밀봉 입찰 수를 섞지 않는다. 공개된 총
                // 입찰 수는 조회 시점에 sealed_bid_count를 더해 만든다.
                () -> assertThat(columnOf(auction.getId(), "bid_count")).isEqualTo(1L),
                () -> assertThat(columnOf(auction.getId(), "sealed_bid_count")).isEqualTo(1L),
                () -> assertThat(trades).hasSize(1),
                () -> assertThat(((Number) trades.get(0)[0]).longValue())
                        .isEqualTo(sealedBidder.getId()),
                () -> assertThat(((Number) trades.get(0)[1]).longValue())
                        .isEqualTo(START_PRICE + (BID_UNIT * 9))
        );
    }

    @Test
    void 공개와_밀봉_입찰이_섞이면_낙찰자_보증금은_유지되고_비낙찰자_보증금은_반환된다() {
        // given
        Member seller = persistMember("batch-refund-seller");
        Member publicLoser = persistMember("batch-refund-public-loser");
        Member sealedWinner = persistMember("batch-refund-sealed-winner");
        UpAuction auction = persistAuction(seller);
        persistHeldDeposit(auction, publicLoser);
        persistHeldDeposit(auction, sealedWinner);
        updateMemberPoints(publicLoser.getId(), 1_900_000L, 100_000L);
        updateMemberPoints(sealedWinner.getId(), 1_900_000L, 100_000L);
        placePublicBid(auction.getId(), publicLoser.getId(), START_PRICE + BID_UNIT);
        long sealedPrice = START_PRICE + (BID_UNIT * 9);
        placeSealedBid(auction.getId(), sealedWinner.getId(), sealedPrice);
        endNow(auction.getId());

        // when
        auctionClosingService.closeBatch(AuctionStatus.BID_ONGOING, BATCH_SIZE);

        // then
        List<Object[]> trades = tradesOf(auction.getId());
        assertAll(
                () -> assertThat(statusOf(auction.getId())).isEqualTo("COMPLETED"),
                () -> assertThat(trades).hasSize(1),
                () -> assertThat(((Number) trades.get(0)[0]).longValue())
                        .isEqualTo(sealedWinner.getId()),
                () -> assertThat(depositStatusOf(auction.getId(), sealedWinner.getId()))
                        .isEqualTo(DepositStatus.HELD.name()),
                () -> assertThat(depositStatusOf(auction.getId(), publicLoser.getId()))
                        .isEqualTo(DepositStatus.REFUNDED.name()),
                () -> assertThat(pointsOf(sealedWinner.getId()))
                        .isEqualTo(new PointSnapshot(1_900_000L, 100_000L)),
                () -> assertThat(pointsOf(publicLoser.getId()))
                        .isEqualTo(new PointSnapshot(2_000_000L, 0L))
        );
    }

    @Test
    void 공개와_밀봉_최고가가_같으면_공개입찰자가_낙찰된다() {
        // given
        Member seller = persistMember("batch-tie-seller");
        Member publicBidder = persistMember("batch-tie-public");
        Member sealedBidder = persistMember("batch-tie-sealed");
        UpAuction auction = persistAuction(seller);
        long tiePrice = START_PRICE + (BID_UNIT * 5);
        entityManager.createNativeQuery("""
                        UPDATE auction
                        SET status = 'BID_ONGOING',
                            current_price = :price,
                            current_bidder_id = :publicBidderId,
                            sealed_top_price = :price,
                            sealed_top_bidder_id = :sealedBidderId
                        WHERE id = :auctionId
                        """)
                .setParameter("price", tiePrice)
                .setParameter("publicBidderId", publicBidder.getId())
                .setParameter("sealedBidderId", sealedBidder.getId())
                .setParameter("auctionId", auction.getId())
                .executeUpdate();
        entityManager.clear();
        endNow(auction.getId());

        // when
        auctionClosingService.closeBatch(AuctionStatus.BID_ONGOING, BATCH_SIZE);

        // then
        List<Object[]> trades = tradesOf(auction.getId());
        assertAll(
                () -> assertThat(statusOf(auction.getId())).isEqualTo("COMPLETED"),
                () -> assertThat(trades).hasSize(1),
                () -> assertThat(((Number) trades.get(0)[0]).longValue())
                        .isEqualTo(publicBidder.getId()),
                () -> assertThat(((Number) trades.get(0)[1]).longValue()).isEqualTo(tiePrice)
        );
    }

    @Test
    void 입찰이_없는_경매는_유찰되고_거래를_만들지_않는다() {
        // given
        Member seller = persistMember("batch-unsold-seller");
        UpAuction auction = persistAuction(seller);
        long revisionBeforeClosing = revisionOf(auction.getId());
        endNow(auction.getId());

        // when
        auctionClosingService.closeBatch(AuctionStatus.OPEN, BATCH_SIZE);

        // then
        assertAll(
                () -> assertThat(statusOf(auction.getId())).isEqualTo("UNSOLD"),
                () -> assertThat(columnOf(auction.getId(), "completed_at")).isNotNull(),
                () -> assertThat(revisionOf(auction.getId()))
                        .isEqualTo(revisionBeforeClosing + 1),
                () -> assertThat(tradesOf(auction.getId())).isEmpty()
        );
    }

    @Test
    void 낙찰과_유찰이_섞여도_한_번의_배치로_모두_마감한다() {
        // given
        Member seller = persistMember("batch-mixed-seller");
        Member firstWinner = persistMember("batch-mixed-first");
        Member secondWinner = persistMember("batch-mixed-second");
        UpAuction won = persistAuction(seller);
        UpAuction alsoWon = persistAuction(seller);
        placePublicBid(won.getId(), firstWinner.getId(), START_PRICE + BID_UNIT);
        placePublicBid(alsoWon.getId(), secondWinner.getId(), START_PRICE + (BID_UNIT * 7));
        endNow(won.getId());
        endNow(alsoWon.getId());

        // when
        int closed = auctionClosingService.closeBatch(AuctionStatus.BID_ONGOING, BATCH_SIZE);

        // then
        assertAll(
                () -> assertThat(closed).isGreaterThanOrEqualTo(2),
                () -> assertThat(statusOf(won.getId())).isEqualTo("COMPLETED"),
                () -> assertThat(statusOf(alsoWon.getId())).isEqualTo("COMPLETED"),
                () -> assertThat(columnOf(won.getId(), "current_price"))
                        .isEqualTo(START_PRICE + BID_UNIT),
                () -> assertThat(columnOf(alsoWon.getId(), "current_price"))
                        .isEqualTo(START_PRICE + (BID_UNIT * 7)),
                () -> assertThat(((Number) tradesOf(won.getId()).get(0)[0]).longValue())
                        .isEqualTo(firstWinner.getId()),
                () -> assertThat(((Number) tradesOf(alsoWon.getId()).get(0)[0]).longValue())
                        .isEqualTo(secondWinner.getId())
        );
    }

    @Test
    void 거래가_이미_있는_경매가_섞여도_남은_후보까지_마감한다() {
        // given
        Member seller = persistMember("batch-settled-seller");
        Member settledWinner = persistMember("batch-settled-winner");
        Member nextWinner = persistMember("batch-settled-next");
        UpAuction alreadySettled = persistAuction(seller);
        UpAuction behind = persistAuction(seller);
        long settledPrice = START_PRICE + BID_UNIT;
        placePublicBid(alreadySettled.getId(), settledWinner.getId(), settledPrice);
        placePublicBid(behind.getId(), nextWinner.getId(), START_PRICE + (BID_UNIT * 2));
        // 앞선 배치가 거래만 남기고 끊긴 상태. 예전에는 여기서 유니크 제약으로 죽어
        // 뒤에 줄 선 후보가 통째로 마감되지 않았다.
        insertTradeWithoutClosing(alreadySettled.getId(), settledWinner.getId(), settledPrice);
        endNow(alreadySettled.getId());
        endNow(behind.getId());

        // when
        int closed = auctionClosingService.closeBatch(AuctionStatus.BID_ONGOING, BATCH_SIZE);

        // then
        assertAll(
                () -> assertThat(closed).isGreaterThanOrEqualTo(2),
                () -> assertThat(statusOf(alreadySettled.getId())).isEqualTo("COMPLETED"),
                () -> assertThat(statusOf(behind.getId())).isEqualTo("COMPLETED"),
                () -> assertThat(tradesOf(alreadySettled.getId())).hasSize(1),
                () -> assertThat(((Number) tradesOf(alreadySettled.getId()).get(0)[1]).longValue())
                        .isEqualTo(settledPrice),
                () -> assertThat(tradesOf(behind.getId())).hasSize(1)
        );
    }

    @Test
    void 남은_후보가_없으면_아무것도_처리하지_않는다() {

        int drained;
        do {
            drained = auctionClosingService.closeBatch(AuctionStatus.OPEN, BATCH_SIZE);
        } while (drained == BATCH_SIZE);

        // when
        int closed = auctionClosingService.closeBatch(AuctionStatus.OPEN, BATCH_SIZE);

        // then
        assertThat(closed).isZero();
    }

    private void placePublicBid(Long auctionId, Long bidderId, long price) {
        int updated = auctionRepository.updateCurrentPriceForBid(
                auctionId,
                bidderId,
                price,
                BID_UNIT
        );
        assertThat(updated).isEqualTo(1);
    }

    private void placeSealedBid(Long auctionId, Long bidderId, long price) {
        entityManager.createNativeQuery("""
                        UPDATE auction
                        SET ended_at = SYSDATE(6) + INTERVAL 2 MINUTE
                        WHERE id = :auctionId
                        """)
                .setParameter("auctionId", auctionId)
                .executeUpdate();
        entityManager.clear();

        // BidService와 같은 순서로 첫 밀봉입찰과 후속 밀봉입찰을 나눠 시도한다.
        int updated = auctionRepository.tryUpdateAuctionForSealedBid(
                auctionId,
                bidderId,
                price,
                BID_UNIT,
                AuctionStatus.OPEN.name(),
                1
        );
        if (updated == 0) {
            updated = auctionRepository.tryUpdateAuctionForSealedBid(
                    auctionId,
                    bidderId,
                    price,
                    BID_UNIT,
                    AuctionStatus.BID_ONGOING.name(),
                    0
            );
        }
        assertThat(updated).isEqualTo(1);
    }

    private void persistHeldDeposit(UpAuction auction, Member bidder) {
        entityManager.persist(AuctionDeposit.builder()
                .auction(auction)
                .member(bidder)
                .reservedAmount(100_000L)
                .status(DepositStatus.HELD)
                .build());
        entityManager.flush();
    }

    private void updateMemberPoints(Long memberId, long totalPoint, long lockedPoint) {
        entityManager.createNativeQuery("""
                        UPDATE member
                        SET total_point = :totalPoint,
                            locked_point = :lockedPoint
                        WHERE id = :memberId
                        """)
                .setParameter("totalPoint", totalPoint)
                .setParameter("lockedPoint", lockedPoint)
                .setParameter("memberId", memberId)
                .executeUpdate();
        entityManager.clear();
    }

    private void insertTradeWithoutClosing(Long auctionId, Long buyerId, long finalPrice) {
        entityManager.createNativeQuery("""
                        INSERT INTO auction_trade
                            (auction_id, buyer_id, status, final_price,
                             purchased_at, created_at, last_modified_at)
                        VALUES (:auctionId, :buyerId, 'WAITING_CONFIRM', :finalPrice,
                                SYSDATE(6), SYSDATE(6), SYSDATE(6))
                        """)
                .setParameter("auctionId", auctionId)
                .setParameter("buyerId", buyerId)
                .setParameter("finalPrice", finalPrice)
                .executeUpdate();
        entityManager.clear();
    }

    private void endNow(Long auctionId) {
        entityManager.createNativeQuery("""
                        UPDATE auction
                        SET ended_at = SYSDATE(6) - INTERVAL 1 SECOND
                        WHERE id = :auctionId
                        """)
                .setParameter("auctionId", auctionId)
                .executeUpdate();
        entityManager.clear();
    }

    private String statusOf(Long auctionId) {
        entityManager.clear();
        return (String) entityManager.createNativeQuery(
                        "SELECT status FROM auction WHERE id = :auctionId"
                )
                .setParameter("auctionId", auctionId)
                .getSingleResult();
    }

    private Long columnOf(Long auctionId, String column) {
        entityManager.clear();
        try {
            Object value = entityManager.createNativeQuery(
                            "SELECT " + column + " FROM auction WHERE id = :auctionId"
                    )
                    .setParameter("auctionId", auctionId)
                    .getSingleResult();
            if (value == null) {
                return null;
            }
            return value instanceof Number number ? number.longValue() : 1L;
        } catch (NoResultException exception) {
            return null;
        }
    }

    private String depositStatusOf(Long auctionId, Long memberId) {
        entityManager.clear();
        return (String) entityManager.createNativeQuery("""
                        SELECT status
                        FROM auction_deposit
                        WHERE auction_id = :auctionId
                          AND member_id = :memberId
                        """)
                .setParameter("auctionId", auctionId)
                .setParameter("memberId", memberId)
                .getSingleResult();
    }

    private PointSnapshot pointsOf(Long memberId) {
        entityManager.clear();
        Object[] points = (Object[]) entityManager.createNativeQuery("""
                        SELECT total_point, locked_point
                        FROM member
                        WHERE id = :memberId
                        """)
                .setParameter("memberId", memberId)
                .getSingleResult();
        return new PointSnapshot(
                ((Number) points[0]).longValue(),
                ((Number) points[1]).longValue()
        );
    }

    private long revisionOf(Long auctionId) {
        Long revision = columnOf(auctionId, "revision");
        return revision == null ? 0L : revision;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> tradesOf(Long auctionId) {
        entityManager.clear();
        return entityManager.createNativeQuery("""
                        SELECT buyer_id, final_price, status
                        FROM auction_trade
                        WHERE auction_id = :auctionId
                        """)
                .setParameter("auctionId", auctionId)
                .getResultList();
    }

    private UpAuction persistAuction(Member seller) {
        UpAuction auction = UpAuction.builder()
                .seller(seller)
                .title("배치 마감 통합 테스트")
                .description("선점부터 낙찰·유찰·거래 적재까지 한 배치로 검증")
                .status(AuctionStatus.OPEN)
                .category(AuctionCategory.HOUSEHOLD)
                .startPrice(START_PRICE)
                .endedAt(LocalDateTime.now().plusDays(1))
                .tradeType(TradeType.DELIVERY)
                .contact("01012345678")
                .buyNowPrice(300_000L)
                .build();
        entityManager.persist(auction);
        entityManager.flush();
        return auction;
    }

    private Member persistMember(String prefix) {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);
        Member member = Member.builder()
                .email(prefix + "-" + suffix + "@example.com")
                .password("encoded-password")
                .name("통합테스트")
                .phoneNumber("01012345678")
                .nickname("n" + suffix)
                .status(MemberStatus.ACTIVE)
                .build();
        entityManager.persist(member);
        return member;
    }

    private record PointSnapshot(long totalPoint, long lockedPoint) {
    }
}
