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

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private EntityManager entityManager;

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

    private UpAuction persistAuction(String title, LocalDateTime endedAt) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Member seller = persistMember("seller-" + suffix, "판매" + suffix);
        UpAuction auction = UpAuction.builder()
                .seller(seller)
                .title(title)
                .description("목록 조회 통합 테스트")
                .status(AuctionStatus.OPEN)
                .category(AuctionCategory.HOUSEHOLD)
                .startPrice(100_000L)
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
