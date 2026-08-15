package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.TradeType;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.storage.s3.bucket=test-bucket")
@Transactional
class AuctionRepositoryIntegrationTest {

    private static final long BID_UNIT = 1_000L;
    private static final long START_PRICE = 100_000L;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * revision 증가가 조건부 UPDATE 안으로 들어가 mock 검증으로는 확인할 수 없으므로,
     * 화면 갱신의 전제인 "현재가가 오른 입찰에서만 revision이 오른다"를 실제 쿼리로 검증한다.
     */
    @Test
    void 조건부_현재가_갱신에_성공하면_revision도_함께_오른다() {
        // given
        Member seller = persistMember("rev-seller");
        Member bidder = persistMember("rev-bidder");
        UpAuction auction = persistAuction(seller);

        // when
        int updated = auctionRepository.updateCurrentPriceForBid(
                auction.getId(),
                bidder.getId(),
                START_PRICE + BID_UNIT,
                BID_UNIT
        );

        // then
        assertThat(updated).isEqualTo(1);
        assertThat(revisionOf(auction.getId())).isEqualTo(1L);
    }

    @Test
    void 현재가_조건을_넘지_못해_갱신에_실패하면_revision이_오르지_않는다() {
        // given
        Member seller = persistMember("norev-seller");
        Member bidder = persistMember("norev-bidder");
        UpAuction auction = persistAuction(seller);

        // when: 시작가와 같은 값이라 한 호가 위 조건을 만족하지 못한다.
        int updated = auctionRepository.updateCurrentPriceForBid(
                auction.getId(),
                bidder.getId(),
                START_PRICE,
                BID_UNIT
        );

        // then
        assertThat(updated).isZero();
        assertThat(revisionOf(auction.getId())).isZero();
    }

    @Test
    void 첫_밀봉입찰로_공개_상태가_바뀌면_revision이_오른다() {
        // given
        Member seller = persistMember("sealed-rev-seller");
        Member bidder = persistMember("sealed-rev-bidder");
        UpAuction auction = persistAuction(seller);
        moveToSealedWindow(auction.getId());

        // when
        int updated = auctionRepository.tryUpdateAuctionForSealedBid(
                auction.getId(),
                bidder.getId(),
                START_PRICE + BID_UNIT,
                BID_UNIT
        );

        // then
        assertThat(updated).isEqualTo(1);
        assertThat(revisionOf(auction.getId())).isEqualTo(1L);
    }

    @Test
    void 진행중인_경매의_추가_밀봉입찰은_revision으로_비공개_입찰수를_노출하지_않는다() {
        // given
        Member seller = persistMember("sealed-private-seller");
        Member firstBidder = persistMember("sealed-private-first");
        Member secondBidder = persistMember("sealed-private-second");
        UpAuction auction = persistAuction(seller);
        moveToSealedWindow(auction.getId());
        auctionRepository.tryUpdateAuctionForSealedBid(
                auction.getId(),
                firstBidder.getId(),
                START_PRICE + BID_UNIT,
                BID_UNIT
        );

        // when
        int updated = auctionRepository.tryUpdateAuctionForSealedBid(
                auction.getId(),
                secondBidder.getId(),
                START_PRICE + (BID_UNIT * 2),
                BID_UNIT
        );

        // then
        assertThat(updated).isEqualTo(1);
        assertThat(revisionOf(auction.getId())).isEqualTo(1L);
    }

    @Test
    void 승인된_밀봉입찰은_공개입찰과_별도인_밀봉입찰수를_누적한다() {
        // given
        Member seller = persistMember("sealed-count-seller");
        Member firstBidder = persistMember("sealed-count-first");
        Member secondBidder = persistMember("sealed-count-second");
        UpAuction auction = persistAuction(seller);
        moveToSealedWindow(auction.getId());

        // when
        auctionRepository.tryUpdateAuctionForSealedBid(
                auction.getId(),
                firstBidder.getId(),
                START_PRICE + BID_UNIT,
                BID_UNIT
        );
        auctionRepository.tryUpdateAuctionForSealedBid(
                auction.getId(),
                secondBidder.getId(),
                START_PRICE + (BID_UNIT * 2),
                BID_UNIT
        );

        // then
        assertThat(sealedBidCountOf(auction.getId())).isEqualTo(2L);
    }

    @Test
    void 판매_물품은_최신_3건까지만_조회한다() {
        // given
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member seller = Member.builder()
                .email("seller-" + suffix + "@example.com")
                .password("encoded-password")
                .name("통합테스트")
                .phoneNumber("01012345678")
                .nickname("판매" + suffix)
                .status(MemberStatus.ACTIVE)
                .build();
        entityManager.persist(seller);
        for (int index = 0; index < 4; index++) {
            entityManager.persist(UpAuction.builder()
                    .seller(seller)
                    .title("판매 물품 " + index)
                    .description("판매 물품 미리보기 제한 테스트")
                    .status(AuctionStatus.OPEN)
                    .category(AuctionCategory.HOUSEHOLD)
                    .startPrice(100_000L)
                    .endedAt(LocalDateTime.now().plusDays(1))
                    .tradeType(TradeType.DELIVERY)
                    .contact("01012345678")
                    .build());
        }
        entityManager.flush();
        entityManager.clear();

        // when
        List<Auction> sellingItems = auctionRepository
                .findTop3BySellerIdOrderByIdDesc(seller.getId());

        // then
        assertThat(sellingItems).hasSize(3);
        assertThat(sellingItems)
                .extracting(Auction::getId)
                .isSortedAccordingTo((left, right) -> Long.compare(right, left));
    }
    private long revisionOf(Long auctionId) {
        entityManager.clear();
        Number revision = (Number) entityManager.createNativeQuery("""
                        SELECT revision
                        FROM auction
                        WHERE id = :auctionId
                        """)
                .setParameter("auctionId", auctionId)
                .getSingleResult();
        return revision.longValue();
    }

    private long sealedBidCountOf(Long auctionId) {
        entityManager.clear();
        Number bidCount = (Number) entityManager.createNativeQuery("""
                        SELECT sealed_bid_count
                        FROM auction
                        WHERE id = :auctionId
                        """)
                .setParameter("auctionId", auctionId)
                .getSingleResult();
        return bidCount.longValue();
    }

    private UpAuction persistAuction(Member seller) {
        UpAuction auction = UpAuction.builder()
                .seller(seller)
                .title("조건부 갱신 통합 테스트")
                .description("현재가 갱신과 revision 증가를 한 쿼리로 검증")
                .status(AuctionStatus.OPEN)
                .category(AuctionCategory.HOUSEHOLD)
                .startPrice(START_PRICE)
                .endedAt(LocalDateTime.now().plusDays(1))
                .tradeType(TradeType.DELIVERY)
                .contact("01012345678")
                .buyNowPrice(300_000L)
                .build();
        entityManager.persist(auction);
        // 조건부 UPDATE가 방금 넣은 행을 보도록 먼저 flush한다.
        entityManager.flush();
        return auction;
    }

    private void moveToSealedWindow(Long auctionId) {
        entityManager.createNativeQuery("""
                        update auction
                        set ended_at = SYSDATE(6) + INTERVAL 2 MINUTE
                        where id = :auctionId
                        """)
                .setParameter("auctionId", auctionId)
                .executeUpdate();
        entityManager.clear();
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
                // nickname은 10자 제한이라 무작위 접미사만 쓴다.
                .nickname("n" + suffix)
                .status(MemberStatus.ACTIVE)
                .build();
        entityManager.persist(member);
        return member;
    }

}
