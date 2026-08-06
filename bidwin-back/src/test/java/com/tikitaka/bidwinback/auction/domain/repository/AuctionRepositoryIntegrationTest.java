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
}
