package com.tikitaka.bidwinback.auction.domain.repository;

import com.tikitaka.bidwinback.auction.domain.entity.Image;
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
class ImageRepositoryIntegrationTest {

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 경매당_가장_먼저_등록한_이미지_한_장만_반환한다() {
        UpAuction auction = persistAuction("대표 이미지 조회 테스트");

        Image first = persistImage(auction);
        persistImage(auction);
        persistImage(auction);
        entityManager.flush();
        entityManager.clear();

        List<Image> result = imageRepository.findFirstImageByAuctionIds(List.of(auction.getId()));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(first.getId());
    }

    @Test
    void 여러_경매를_함께_조회하면_경매별로_대표_이미지가_하나씩_반환된다() {
        UpAuction auctionA = persistAuction("경매 A");
        UpAuction auctionB = persistAuction("경매 B");

        Image firstOfA = persistImage(auctionA);
        persistImage(auctionA);
        Image firstOfB = persistImage(auctionB);
        persistImage(auctionB);
        entityManager.flush();
        entityManager.clear();

        List<Image> result = imageRepository.findFirstImageByAuctionIds(List.of(auctionA.getId(), auctionB.getId()));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Image::getId)
                .containsExactlyInAnyOrder(firstOfA.getId(), firstOfB.getId());
    }

    private Image persistImage(UpAuction auction) {
        Image image = Image.builder()
                .auction(auction)
                .objectKey("images/" + UUID.randomUUID())
                .build();
        entityManager.persist(image);
        return image;
    }

    private UpAuction persistAuction(String title) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Member seller = persistMember("seller-" + suffix, "판매" + suffix);
        UpAuction auction = UpAuction.builder()
                .seller(seller)
                .title(title)
                .description("대표 이미지 조회 통합 테스트")
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
