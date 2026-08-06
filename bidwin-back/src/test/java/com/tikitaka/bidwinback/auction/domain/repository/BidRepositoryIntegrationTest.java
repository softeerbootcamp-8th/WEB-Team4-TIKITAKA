package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.Bid;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;
import com.tikitaka.bidwinback.auction.domain.enums.TradeType;
import com.tikitaka.bidwinback.auction.domain.repository.dto.BidHistoryRow;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.storage.s3.bucket=test-bucket")
@Transactional
class BidRepositoryIntegrationTest {

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 전체_건수와_동일_시각의_최신_10건을_조회한다() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);
        Member seller = persistMember("seller-" + suffix, "판매" + suffix);
        Member bidder = persistMember("bidder-" + suffix, "입찰" + suffix);
        UpAuction auction = UpAuction.builder()
                .seller(seller)
                .title("입찰 내역 통합 테스트")
                .description("입찰 내역 Repository 쿼리 검증")
                .status(AuctionStatus.OPEN)
                .category(AuctionCategory.HOUSEHOLD)
                .startPrice(100_000L)
                .endedAt(LocalDateTime.now().plusDays(1))
                .tradeType(TradeType.DELIVERY)
                .contact("01012345678")
                .buyNowPrice(300_000L)
                .build();
        entityManager.persist(auction);

        List<Bid> bids = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            Bid bid = Bid.builder()
                    .auction(auction)
                    .bidder(bidder)
                    .price(100_000L + index * 10_000L)
                    .status(BidStatus.UP)
                    .build();
            entityManager.persist(bid);
            bids.add(bid);
        }
        entityManager.flush();

        LocalDateTime biddedAt = LocalDateTime.of(2026, 8, 3, 12, 0);
        entityManager.createNativeQuery("""
                        update bid
                        set created_at = :biddedAt
                        where auction_id = :auctionId
                        """)
                .setParameter("biddedAt", biddedAt)
                .setParameter("auctionId", auction.getId())
                .executeUpdate();
        entityManager.clear();

        long bidCount = bidRepository.countVisibleByAuctionId(
                auction.getId(),
                BidStatus.SEALED,
                false
        );
        List<BidHistoryRow> bidHistory =
                bidRepository.findVisibleHistoryByAuctionId(
                        auction.getId(),
                        BidStatus.SEALED,
                        false
                );

        List<Long> expectedIds = new ArrayList<>();
        for (int index = bids.size() - 1; index >= 2; index--) {
            expectedIds.add(bids.get(index).getId());
        }

        assertThat(bidCount).isEqualTo(12L);
        assertThat(bidHistory).hasSize(10);
        assertThat(bidHistory)
                .extracting(BidHistoryRow::id)
                .containsExactlyElementsOf(expectedIds);
        assertThat(bidHistory.getFirst()).satisfies(row -> {
            assertThat(row.bidderId()).isEqualTo(bidder.getId());
            assertThat(row.bidderNickname()).isEqualTo(bidder.getNickname());
            assertThat(row.amount()).isEqualTo(210_000L);
            assertThat(row.biddedAt()).isEqualTo(biddedAt);
        });
    }

    @Test
    void 입찰이_없으면_null이고_입찰이_있으면_최고가를_조회한다() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);
        Member seller = persistMember("max-seller-" + suffix, "판매" + suffix);
        Member bidder = persistMember("max-bidder-" + suffix, "입찰" + suffix);
        UpAuction auction = UpAuction.builder()
                .seller(seller)
                .title("최고가 조회 통합 테스트")
                .description("레거시 현재가 폴백 쿼리 검증")
                .status(AuctionStatus.OPEN)
                .category(AuctionCategory.HOUSEHOLD)
                .startPrice(100_000L)
                .endedAt(LocalDateTime.now().plusDays(1))
                .tradeType(TradeType.DELIVERY)
                .contact("01012345678")
                .buyNowPrice(300_000L)
                .build();
        entityManager.persist(auction);
        entityManager.flush();

        assertThat(bidRepository.findHighestPriceByAuctionIdAndStatus(
                auction.getId(),
                BidStatus.UP
        )).isNull();

        entityManager.persist(Bid.builder()
                .auction(auction)
                .bidder(bidder)
                .price(101_000L)
                .status(BidStatus.UP)
                .build());
        entityManager.persist(Bid.builder()
                .auction(auction)
                .bidder(bidder)
                .price(103_000L)
                .status(BidStatus.UP)
                .build());
        entityManager.flush();
        entityManager.clear();

        assertThat(bidRepository.findHighestPriceByAuctionIdAndStatus(
                auction.getId(),
                BidStatus.UP
        ))
                .isEqualTo(103_000L);
    }

    @Test
    void SEALED_입찰은_경매_상태에_따라_비공개하거나_공개한다() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);
        Member seller = persistMember("sealed-seller-" + suffix, "판매" + suffix);
        Member bidder = persistMember("sealed-bidder-" + suffix, "입찰" + suffix);
        UpAuction auction = UpAuction.builder()
                .seller(seller)
                .title("밀봉 입찰 조회 통합 테스트")
                .description("밀봉 입찰 비공개 검증")
                .status(AuctionStatus.BID_ONGOING)
                .category(AuctionCategory.HOUSEHOLD)
                .startPrice(100_000L)
                .endedAt(LocalDateTime.now().plusMinutes(4))
                .tradeType(TradeType.DELIVERY)
                .contact("01012345678")
                .buyNowPrice(300_000L)
                .build();
        entityManager.persist(auction);
        entityManager.persist(Bid.builder()
                .auction(auction)
                .bidder(bidder)
                .price(101_000L)
                .status(BidStatus.UP)
                .build());
        entityManager.persist(Bid.builder()
                .auction(auction)
                .bidder(bidder)
                .price(150_000L)
                .status(BidStatus.SEALED)
                .build());
        entityManager.flush();
        entityManager.clear();

        assertThat(bidRepository.countVisibleByAuctionId(
                auction.getId(),
                BidStatus.SEALED,
                false
        )).isEqualTo(1L);
        assertThat(bidRepository.findVisibleHistoryByAuctionId(
                auction.getId(),
                BidStatus.SEALED,
                false
        )).extracting(BidHistoryRow::amount).containsExactly(101_000L);
        assertThat(bidRepository.countVisibleByAuctionId(
                auction.getId(),
                BidStatus.SEALED,
                true
        )).isEqualTo(2L);
        assertThat(bidRepository.findVisibleHistoryByAuctionId(
                auction.getId(),
                BidStatus.SEALED,
                true
        )).extracting(BidHistoryRow::amount).containsExactly(150_000L, 101_000L);
        assertThat(bidRepository.findHighestPriceByAuctionIdAndStatus(
                auction.getId(),
                BidStatus.UP
        )).isEqualTo(101_000L);
    }

    private Member persistMember(String identifier, String nickname) {
        Member member = Member.builder()
                .email(identifier + "@example.com")
                .password("encoded-password")
                .name("통합테스트")
                .phoneNumber("01012345678")
                .nickname(nickname)
                .status(MemberStatus.ACTIVE)
                .build();
        entityManager.persist(member);
        return member;
    }
}
