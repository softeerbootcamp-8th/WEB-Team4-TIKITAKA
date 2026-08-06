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
    void asOf_이후에_생성된_경매는_같은_asOf로_조회한_목록에_나타나지_않는다() {
        LocalDateTime asOf = LocalDateTime.of(2026, 8, 3, 12, 0);

        UpAuction visible = persistAuction("asOf 이전 등록", asOf.plusDays(1));
        overrideCreatedAt(visible, asOf);

        UpAuction createdLater = persistAuction("asOf 이후 등록", asOf.plusDays(1));
        overrideCreatedAt(createdLater, asOf.plusMinutes(1));

        entityManager.clear();

        List<Auction> result = auctionRepository.findAllForList(null, asOf);

        assertThat(result).extracting(Auction::getId)
                .contains(visible.getId())
                .doesNotContain(createdLater.getId());
    }

    @Test
    void 마감_시각이_asOf_이전인_경매는_목록에서_제외된다() {
        LocalDateTime asOf = LocalDateTime.of(2026, 8, 3, 12, 0);

        UpAuction stillOpen = persistAuction("아직 진행중", asOf.plusDays(1));
        overrideCreatedAt(stillOpen, asOf.minusDays(1));

        UpAuction alreadyEnded = persistAuction("이미 마감", asOf.minusMinutes(1));
        overrideCreatedAt(alreadyEnded, asOf.minusDays(1));

        entityManager.clear();

        List<Auction> result = auctionRepository.findAllForList(null, asOf);

        assertThat(result).extracting(Auction::getId)
                .contains(stillOpen.getId())
                .doesNotContain(alreadyEnded.getId());
    }

    @Test
    void asOf_이전에_완료_처리된_경매는_목록에서_제외된다() {
        LocalDateTime asOf = LocalDateTime.of(2026, 8, 3, 12, 0);

        UpAuction notCompleted = persistAuction("미완료", asOf.plusDays(1));
        overrideCreatedAt(notCompleted, asOf.minusDays(1));

        UpAuction completed = persistAuction("낙찰 완료", asOf.plusDays(1));
        overrideCreatedAt(completed, asOf.minusDays(1));
        overrideCompletedAt(completed, asOf.minusMinutes(1));

        entityManager.clear();

        List<Auction> result = auctionRepository.findAllForList(null, asOf);

        assertThat(result).extracting(Auction::getId)
                .contains(notCompleted.getId())
                .doesNotContain(completed.getId());
    }

    @Test
    void asOf_이후에_완료_처리된_경매는_같은_asOf_페이지_조회에서_계속_노출된다() {
        LocalDateTime asOf = LocalDateTime.of(2026, 8, 3, 12, 0);

        UpAuction completedAfterAsOf = persistAuction("조회 도중 즉시구매 체결", asOf.plusDays(1));
        overrideCreatedAt(completedAfterAsOf, asOf.minusDays(1));
        overrideCompletedAt(completedAfterAsOf, asOf.plusMinutes(1));

        entityManager.clear();

        List<Auction> result = auctionRepository.findAllForList(null, asOf);

        assertThat(result).extracting(Auction::getId)
                .contains(completedAfterAsOf.getId());
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

    private UpAuction persistAuction(String title, LocalDateTime endedAt) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Member seller = persistMember("seller-" + suffix, "판매" + suffix);
        UpAuction auction = UpAuction.builder()
                .seller(seller)
                .title(title)
                .description("목록 조회 통합 테스트")
                .status(AuctionStatus.OPEN)
                .category(AuctionCategory.HOUSEHOLD)
                .startPrice(START_PRICE)
                .endedAt(endedAt)
                .tradeType(TradeType.DELIVERY)
                .contact("01012345678")
                .buyNowPrice(300_000L)
                .build();
        entityManager.persist(auction);
        entityManager.flush();
        return auction;
    }

    private void overrideCreatedAt(Auction auction, LocalDateTime createdAt) {
        entityManager.createNativeQuery("""
                        update auction
                        set created_at = :createdAt
                        where id = :auctionId
                        """)
                .setParameter("createdAt", createdAt)
                .setParameter("auctionId", auction.getId())
                .executeUpdate();
    }

    private void overrideCompletedAt(Auction auction, LocalDateTime completedAt) {
        entityManager.createNativeQuery("""
                        update auction
                        set completed_at = :completedAt
                        where id = :auctionId
                        """)
                .setParameter("completedAt", completedAt)
                .setParameter("auctionId", auction.getId())
                .executeUpdate();
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
