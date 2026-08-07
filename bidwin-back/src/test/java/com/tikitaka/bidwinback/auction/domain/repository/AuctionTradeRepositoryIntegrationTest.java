package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
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
class AuctionTradeRepositoryIntegrationTest {

    @Autowired
    private AuctionTradeRepository auctionTradeRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 판매자는_CONFIRMED이고_구매자는_WAITING_CONFIRM인_거래만_조회한다() {
        // given
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member member = persistMember("m" + suffix);
        Member counterparty = persistMember("c" + suffix);

        AuctionTrade sellerConfirmed = persistTrade(member, counterparty, TradeStatus.CONFIRMED, "판매 확인 완료");
        persistTrade(member, counterparty, TradeStatus.WAITING_CONFIRM, "판매 확인 대기");
        AuctionTrade buyerWaiting = persistTrade(counterparty, member, TradeStatus.WAITING_CONFIRM, "구매 확인 대기");
        persistTrade(counterparty, member, TradeStatus.CONFIRMED, "구매 확인 완료");
        entityManager.flush();
        entityManager.clear();

        // when
        List<AuctionTrade> activeTrades = auctionTradeRepository.findActiveTrades(
                member.getId(),
                TradeStatus.WAITING_CONFIRM,
                TradeStatus.CONFIRMED
        );

        // then
        assertThat(activeTrades)
                .extracting(AuctionTrade::getId)
                .containsExactlyInAnyOrder(sellerConfirmed.getId(), buyerWaiting.getId());
    }

    @Test
    void 구매_물품은_최신_3건까지만_조회한다() {
        // given
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member seller = persistMember("ls" + suffix);
        Member buyer = persistMember("lb" + suffix);
        for (int index = 0; index < 4; index++) {
            persistTrade(seller, buyer, TradeStatus.COMPLETED, "구매 물품 " + index);
        }
        entityManager.flush();
        entityManager.clear();

        // when
        List<AuctionTrade> buyingItems = auctionTradeRepository.findBuyingItems(
                buyer.getId(),
                List.of(TradeStatus.COMPLETED)
        );

        // then
        assertThat(buyingItems).hasSize(3);
    }

    @Test
    void 진행_중_거래는_모두_조회한다() {
        // given
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member seller = persistMember("as" + suffix);
        Member buyer = persistMember("ab" + suffix);
        for (int index = 0; index < 5; index++) {
            persistTrade(seller, buyer, TradeStatus.WAITING_CONFIRM, "진행 거래 " + index);
        }
        entityManager.flush();
        entityManager.clear();

        // when
        List<AuctionTrade> activeTrades = auctionTradeRepository.findActiveTrades(
                buyer.getId(),
                TradeStatus.WAITING_CONFIRM,
                TradeStatus.CONFIRMED
        );

        // then
        assertThat(activeTrades).hasSize(5);
    }

    private Member persistMember(String identifier) {
        Member member = Member.builder()
                .email(identifier + "@example.com")
                .password("encoded-password")
                .name("통합테스트")
                .phoneNumber("01012345678")
                .nickname(identifier)
                .status(MemberStatus.ACTIVE)
                .build();
        entityManager.persist(member);
        return member;
    }

    private AuctionTrade persistTrade(
            Member seller,
            Member buyer,
            TradeStatus status,
            String title
    ) {
        UpAuction auction = UpAuction.builder()
                .seller(seller)
                .title(title)
                .description("진행 중 거래 배너 조회 테스트")
                .status(AuctionStatus.COMPLETED)
                .category(AuctionCategory.HOUSEHOLD)
                .startPrice(100_000L)
                .endedAt(LocalDateTime.now().minusHours(1))
                .tradeType(TradeType.DELIVERY)
                .contact("01012345678")
                .build();
        entityManager.persist(auction);

        AuctionTrade trade = AuctionTrade.builder()
                .auction(auction)
                .buyer(buyer)
                .status(status)
                .finalPrice(120_000L)
                .purchasedAt(LocalDateTime.now())
                .build();
        entityManager.persist(trade);
        return trade;
    }
}
