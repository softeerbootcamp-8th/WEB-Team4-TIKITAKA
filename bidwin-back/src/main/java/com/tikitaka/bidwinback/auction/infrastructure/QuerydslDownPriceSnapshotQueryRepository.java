package com.tikitaka.bidwinback.auction.infrastructure;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.repository.DownPriceSnapshotQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.tikitaka.bidwinback.auction.domain.entity.QAuction.auction;
import static com.tikitaka.bidwinback.auction.domain.entity.QDownPriceSnapshot.downPriceSnapshot;

@Repository
public class QuerydslDownPriceSnapshotQueryRepository
        implements DownPriceSnapshotQueryRepository {

    private final JPAQueryFactory queryFactory;

    public QuerydslDownPriceSnapshotQueryRepository(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Optional<LocalDateTime> findLatestSnapshotAtNotAfter(LocalDateTime asOf) {
        return Optional.ofNullable(queryFactory
                .select(downPriceSnapshot.snapshotAt.max())
                .from(downPriceSnapshot)
                .where(downPriceSnapshot.snapshotAt.loe(asOf))
                .fetchOne());
    }

    @Override
    public long count(LocalDateTime snapshotAt, String keyword) {
        Long count = keyword == null
                ? queryFactory
                        .select(downPriceSnapshot.auctionId.count())
                        .from(downPriceSnapshot)
                        .where(downPriceSnapshot.snapshotAt.eq(snapshotAt))
                        .fetchOne()
                : queryFactory
                        .select(downPriceSnapshot.auctionId.count())
                        .from(downPriceSnapshot)
                        .join(downPriceSnapshot.auction, auction)
                        .where(
                                downPriceSnapshot.snapshotAt.eq(snapshotAt),
                                titleContains(auction.title, keyword)
                        )
                        .fetchOne();
        return count != null ? count : 0L;
    }

    @Override
    public List<AuctionPriceSnapshot> findPage(
            LocalDateTime snapshotAt,
            AuctionSort sort,
            String keyword,
            long offset,
            int limit
    ) {
        List<DownPriceSnapshotDetails> details = keyword == null
                ? queryFactory
                        .select(new QDownPriceSnapshotDetails(
                                downPriceSnapshot.auctionId,
                                downPriceSnapshot.price
                        ))
                        .from(downPriceSnapshot)
                        .where(downPriceSnapshot.snapshotAt.eq(snapshotAt))
                        .orderBy(priceOrderBy(sort))
                        .offset(offset)
                        .limit(limit)
                        .fetch()
                : queryFactory
                        .select(new QDownPriceSnapshotDetails(
                                downPriceSnapshot.auctionId,
                                downPriceSnapshot.price
                        ))
                        .from(downPriceSnapshot)
                        .join(downPriceSnapshot.auction, auction)
                        .where(
                                downPriceSnapshot.snapshotAt.eq(snapshotAt),
                                titleContains(auction.title, keyword)
                        )
                        .orderBy(priceOrderBy(sort))
                        .offset(offset)
                        .limit(limit)
                        .fetch();
        return details.stream()
                .map(DownPriceSnapshotDetails::toSnapshot)
                .toList();
    }

    private OrderSpecifier<?>[] priceOrderBy(AuctionSort sort) {
        return switch (sort) {
            case PRICE_LOW -> new OrderSpecifier<?>[]{
                    downPriceSnapshot.price.asc(),
                    downPriceSnapshot.auctionId.desc()
            };
            case PRICE_HIGH -> new OrderSpecifier<?>[]{
                    downPriceSnapshot.price.desc(),
                    downPriceSnapshot.auctionId.desc()
            };
            case RECOMMENDED, DEADLINE, LATEST ->
                    throw new IllegalArgumentException("가격 정렬이 아닙니다: " + sort);
        };
    }

    private BooleanExpression titleContains(StringExpression title, String keyword) {
        String pattern = "%" + AuctionListKeywordEscaper.escape(keyword) + "%";
        return title.like(pattern, AuctionListKeywordEscaper.LIKE_ESCAPE);
    }
}
