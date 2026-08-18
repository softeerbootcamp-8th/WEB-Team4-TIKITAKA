package com.tikitaka.bidwinback.auction.infrastructure;

import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionListStatusFilter;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.TradeType;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionListQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListRow;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListSearchCondition;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.storage.s3.bucket=test-bucket",
        "app.auction.search.fulltext-enabled=true"
})
class AuctionTitleFullTextIntegrationTest {

    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 8, 18, 12, 0);
    private static final String KEYWORD = "엔그램검증";

    @Autowired
    private AuctionListQueryRepository auctionListQueryRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> auctionIds = new ArrayList<>();
    private Long sellerId;

    @AfterEach
    void cleanUp() {
        if (auctionIds.isEmpty() || sellerId == null) {
            return;
        }
        inTransaction(() -> {
            entityManager.createNativeQuery("""
                            DELETE FROM up_auction
                            WHERE auction_id IN (:auctionIds)
                            """)
                    .setParameter("auctionIds", auctionIds)
                    .executeUpdate();
            entityManager.createNativeQuery("""
                            DELETE FROM auction
                            WHERE id IN (:auctionIds)
                            """)
                    .setParameter("auctionIds", auctionIds)
                    .executeUpdate();
            entityManager.createNativeQuery("""
                            DELETE FROM member
                            WHERE id = :sellerId
                            """)
                    .setParameter("sellerId", sellerId)
                    .executeUpdate();
            return null;
        });
    }

    @Test
    void ngram_설정과_FULLTEXT_인덱스가_적용된다() {
        inTransaction(() -> {
            Number tokenSize = (Number) entityManager.createNativeQuery(
                            "SELECT @@ngram_token_size"
                    )
                    .getSingleResult();
            List<String> indexTypes = entityManager.createNativeQuery("""
                            SELECT index_type
                            FROM information_schema.statistics
                            WHERE table_schema = DATABASE()
                              AND table_name = 'auction'
                              AND index_name = 'idx_auction_title_ngram'
                            """)
                    .getResultList()
                    .stream()
                    .map(String::valueOf)
                    .toList();

            assertThat(tokenSize.intValue()).isEqualTo(2);
            assertThat(indexTypes).containsExactly("FULLTEXT");
            return null;
        });
    }

    @Test
    void 커밋한_제목을_세가지_허용_정렬로_부분검색한다() {
        Fixture fixture = persistFixture();

        assertThat(findIds(AuctionSort.RECOMMENDED))
                .containsExactly(fixture.second(), fixture.third(), fixture.first());
        assertThat(findIds(AuctionSort.LATEST))
                .containsExactly(fixture.third(), fixture.second(), fixture.first());
        assertThat(findIds(AuctionSort.DEADLINE))
                .containsExactly(fixture.second(), fixture.third(), fixture.first());
    }

    private Fixture persistFixture() {
        return inTransaction(() -> {
            Member seller = persistMember();
            UpAuction first = persistUp(seller, "빈티지 엔그램검증 의자", AS_OF.plusHours(3));
            UpAuction second = persistUp(seller, "원목 엔그램검증 의자", AS_OF.plusHours(1));
            UpAuction third = persistUp(seller, "접이식 엔그램검증 의자", AS_OF.plusHours(2));
            UpAuction excluded = persistUp(seller, "검색 제외 책상", AS_OF.plusMinutes(30));
            entityManager.flush();

            updateSearchColumns(first, AS_OF.minusHours(3), 1L);
            updateSearchColumns(second, AS_OF.minusHours(2), 3L);
            updateSearchColumns(third, AS_OF.minusHours(1), 2L);
            updateSearchColumns(excluded, AS_OF, 10L);

            sellerId = seller.getId();
            auctionIds.addAll(List.of(
                    first.getId(),
                    second.getId(),
                    third.getId(),
                    excluded.getId()
            ));
            return new Fixture(first.getId(), second.getId(), third.getId());
        });
    }

    private List<Long> findIds(AuctionSort sort) {
        return inTransaction(() -> auctionListQueryRepository.findPage(
                        new AuctionListSearchCondition(
                                null,
                                sort,
                                KEYWORD,
                                AuctionListStatusFilter.ACTIVE,
                                null,
                                AS_OF
                        ),
                        0,
                        10
                )
                .stream()
                .map(AuctionListRow::auctionId)
                .toList());
    }

    private Member persistMember() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Member member = Member.builder()
                .email("fulltext-" + suffix + "@example.com")
                .password("encoded-password")
                .name("전문검색테스트")
                .phoneNumber("01012345678")
                .nickname("f" + suffix)
                .status(MemberStatus.ACTIVE)
                .build();
        entityManager.persist(member);
        return member;
    }

    private UpAuction persistUp(Member seller, String title, LocalDateTime endedAt) {
        UpAuction auction = UpAuction.builder()
                .seller(seller)
                .title(title)
                .description("n-gram 전문 검색 통합 테스트")
                .status(AuctionStatus.OPEN)
                .category(AuctionCategory.FURNITURE)
                .startPrice(100_000L)
                .endedAt(endedAt)
                .tradeType(TradeType.DELIVERY)
                .contact("01012345678")
                .buyNowPrice(300_000L)
                .build();
        entityManager.persist(auction);
        return auction;
    }

    private void updateSearchColumns(
            UpAuction auction,
            LocalDateTime createdAt,
            long bidCount
    ) {
        entityManager.createNativeQuery("""
                        UPDATE auction
                        SET created_at = :createdAt,
                            started_at = :startedAt,
                            bid_count = :bidCount
                        WHERE id = :auctionId
                        """)
                .setParameter("createdAt", createdAt)
                .setParameter("startedAt", AS_OF.minusDays(1))
                .setParameter("bidCount", bidCount)
                .setParameter("auctionId", auction.getId())
                .executeUpdate();
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager)
                .execute(status -> work.get());
    }

    private record Fixture(long first, long second, long third) {
    }
}
