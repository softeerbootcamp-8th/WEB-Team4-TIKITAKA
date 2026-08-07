package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.Bid;
import com.tikitaka.bidwinback.auction.domain.entity.SealedBid;
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
    private SealedBidRepository sealedBidRepository;

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

        long bidCount = bidRepository.countByAuctionId(auction.getId());
        List<BidHistoryRow> bidHistory =
                bidRepository.findHistoryByAuctionId(auction.getId());

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

        assertThat(bidRepository.findHighestPriceByAuctionId(auction.getId())).isNull();

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

        assertThat(bidRepository.findHighestPriceByAuctionId(auction.getId()))
                .isEqualTo(103_000L);
    }

    @Test
    void 일반입찰과_밀봉입찰에_참여한_경매를_중복_없이_센다() {
        // given
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member seller = persistMember("count-seller-" + suffix, "판매" + suffix);
        Member bidder = persistMember("count-bidder-" + suffix, "입찰" + suffix);
        UpAuction bothAuction = persistAuction(seller, "일반·밀봉 모두 참여");
        UpAuction sealedOnlyAuction = persistAuction(seller, "밀봉만 참여");
        entityManager.persist(Bid.builder()
                .auction(bothAuction)
                .bidder(bidder)
                .price(110_000L)
                .status(BidStatus.UP)
                .build());
        entityManager.persist(SealedBid.builder()
                .auction(bothAuction)
                .bidder(bidder)
                .price(120_000L)
                .build());
        entityManager.persist(SealedBid.builder()
                .auction(sealedOnlyAuction)
                .bidder(bidder)
                .price(130_000L)
                .build());
        entityManager.flush();
        entityManager.clear();

        // when
        long count = bidRepository.countDistinctAuctionByBidderId(bidder.getId());

        // then
        assertThat(count).isEqualTo(2L);
    }

    @Test
    void 밀봉입찰의_전체_건수와_최신_10건을_조회한다() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);
        Member seller = persistMember("sealed-seller-" + suffix, "판매" + suffix);
        UpAuction auction = UpAuction.builder()
                .seller(seller)
                .title("밀봉 입찰 내역 통합 테스트")
                .description("밀봉 입찰 Repository 쿼리 검증")
                .status(AuctionStatus.BID_ONGOING)
                .category(AuctionCategory.HOUSEHOLD)
                .startPrice(100_000L)
                .endedAt(LocalDateTime.now().plusMinutes(4))
                .tradeType(TradeType.DELIVERY)
                .contact("01012345678")
                .buyNowPrice(300_000L)
                .build();
        entityManager.persist(auction);

        List<SealedBid> sealedBids = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            Member bidder = persistMember(
                    "sealed-bidder-" + index + "-" + suffix,
                    "입찰" + index
            );
            SealedBid sealedBid = SealedBid.builder()
                    .auction(auction)
                    .bidder(bidder)
                    .price(101_000L + index * 1_000L)
                    .build();
            entityManager.persist(sealedBid);
            sealedBids.add(sealedBid);
        }
        entityManager.flush();

        LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 3, 12, 0);
        entityManager.createNativeQuery("""
                        update sealed_bid
                        set submitted_at = :submittedAt
                        where auction_id = :auctionId
                        """)
                .setParameter("submittedAt", submittedAt)
                .setParameter("auctionId", auction.getId())
                .executeUpdate();
        entityManager.clear();

        List<Long> expectedIds = new ArrayList<>();
        for (int index = sealedBids.size() - 1; index >= 2; index--) {
            expectedIds.add(sealedBids.get(index).getId());
        }

        assertThat(sealedBidRepository.countByAuctionId(auction.getId())).isEqualTo(12L);
        assertThat(sealedBidRepository.findHistoryByAuctionId(auction.getId()))
                .hasSize(10)
                .extracting(BidHistoryRow::id)
                .containsExactlyElementsOf(expectedIds);
    }

    private UpAuction persistAuction(Member seller, String title) {
        UpAuction auction = UpAuction.builder()
                .seller(seller)
                .title(title)
                .description("경매 참여 횟수 테스트")
                .status(AuctionStatus.OPEN)
                .category(AuctionCategory.HOUSEHOLD)
                .startPrice(100_000L)
                .endedAt(LocalDateTime.now().plusDays(1))
                .tradeType(TradeType.DELIVERY)
                .contact("01012345678")
                .buyNowPrice(300_000L)
                .build();
        entityManager.persist(auction);
        return auction;
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
