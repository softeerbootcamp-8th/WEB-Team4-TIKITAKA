package com.tikitaka.bidwinback.auction.infrastructure;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.Param;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.QImage;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionListQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.BidRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionBidSummary;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListRow;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListSearchCondition;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceCursor;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import com.tikitaka.bidwinback.auction.domain.repository.dto.DownAuctionPriceCandidate;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.tikitaka.bidwinback.auction.domain.entity.QAuction.auction;
import static com.tikitaka.bidwinback.auction.domain.entity.QBid.bid;
import static com.tikitaka.bidwinback.auction.domain.entity.QDownAuction.downAuction;
import static com.tikitaka.bidwinback.auction.domain.entity.QImage.image;
import static com.tikitaka.bidwinback.member.domain.entity.QMember.member;

@Repository
public class QuerydslAuctionListQueryRepository implements AuctionListQueryRepository {

    private static final Param<LocalDateTime> PRICE_AS_OF = new Param<>(
            LocalDateTime.class,
            "auctionListPriceAsOf"
    );
    // {0}=minimumPrice, {1}=startPrice, {2}=startedAt, {3}=asOf, {4}=priceDropInterval, {5}=dropPrice
    private static final String DOWN_CURRENT_PRICE_TEMPLATE = """
            greatest(
                {0},
                {1} - floor(
                    greatest(timestampdiff(minute, {2}, {3}), 0) / greatest({4}, 1)
                ) * {5}
            )
            """;

    private final JPAQueryFactory queryFactory;
    private final BidRepository bidRepository;

    public QuerydslAuctionListQueryRepository(
            EntityManager entityManager,
            BidRepository bidRepository
    ) {
        this.queryFactory = new JPAQueryFactory(entityManager);
        this.bidRepository = bidRepository;
    }

    @Override
    public long count(AuctionListSearchCondition condition) {
        Long count = queryFactory
                .select(auction.count())
                .from(auction)
                .where(searchPredicate(condition))
                .fetchOne();
        return count != null ? count : 0L;
    }

    @Override
    public List<AuctionListRow> findPage(
            AuctionListSearchCondition condition,
            long offset,
            int limit
    ) {
        List<AuctionListMetrics> metrics = switch (condition.sort()) {
            case DEADLINE, LATEST -> findPageByColumnSort(condition, offset, limit);
            case RECOMMENDED -> findPageByAggregateSort(
                    condition,
                    offset,
                    limit
            );
            case PRICE_LOW, PRICE_HIGH ->
                    throw new IllegalArgumentException("가격순은 Top-K 조회를 사용해야 합니다.");
        };
        return findRows(metrics);
    }

    @Override
    public List<AuctionPriceSnapshot> findUpPriceSnapshots(
            AuctionListSearchCondition condition,
            int limit
    ) {
        List<Tuple> prices = queryFactory
                .select(auction.id, auction.currentPrice)
                .from(auction)
                .where(
                        searchPredicate(condition),
                        auction.instanceOf(UpAuction.class)
                )
                .orderBy(upPriceOrderBy(condition.sort()))
                .limit(limit)
                .fetch();
        if (prices.isEmpty()) {
            return List.of();
        }

        return prices.stream()
                .map(tuple -> {
                    long auctionId = requireValue(tuple.get(auction.id), "auctionId");
                    long currentPrice = requireValue(
                            tuple.get(auction.currentPrice),
                            "currentPrice"
                    );
                    return new AuctionPriceSnapshot(
                            auctionId,
                            currentPrice
                    );
                })
                .toList();
    }

    @Override
    public List<DownAuctionPriceCandidate> findDownPriceCandidates(
            AuctionListSearchCondition condition,
            AuctionPriceCursor cursor,
            int limit
    ) {
        List<DownAuctionPriceCandidateDetails> details = switch (condition.sort()) {
            case PRICE_LOW -> findDownCandidatesByMinimumPrice(condition, cursor, limit);
            case PRICE_HIGH -> findDownCandidatesByStartPrice(condition, cursor, limit);
            case RECOMMENDED, DEADLINE, LATEST ->
                    throw new IllegalArgumentException("가격 정렬이 아닙니다: " + condition.sort());
        };
        if (details.isEmpty()) {
            return List.of();
        }

        return details.stream()
                .map(DownAuctionPriceCandidateDetails::toCandidate)
                .toList();
    }

    private List<DownAuctionPriceCandidateDetails> findDownCandidatesByMinimumPrice(
            AuctionListSearchCondition condition,
            AuctionPriceCursor cursor,
            int limit
    ) {
        // 최저가는 하위 테이블에만 있으므로 down_auction의 경계 인덱스부터 읽어야
        // 활성 경매 필터를 위한 auction 조인 전에 LIMIT을 향해 순서대로 스캔할 수 있다.
        return queryFactory
                .select(new QDownAuctionPriceCandidateDetails(
                        downAuction.id,
                        downAuction.startPrice,
                        downAuction.minimumPrice,
                        downAuction.startedAt,
                        downAuction.dropPrice,
                        downAuction.priceDropInterval
                ))
                .from(downAuction)
                .where(
                        downSearchPredicate(condition),
                        minimumPriceCursorAfter(cursor)
                )
                .orderBy(
                        downAuction.minimumPrice.asc(),
                        downAuction.id.desc()
                )
                .limit(limit)
                .fetch();
    }

    private List<DownAuctionPriceCandidateDetails> findDownCandidatesByStartPrice(
            AuctionListSearchCondition condition,
            AuctionPriceCursor cursor,
            int limit
    ) {
        return queryFactory
                .select(new QDownAuctionPriceCandidateDetails(
                        auction.id,
                        auction.startPrice,
                        downAuction.minimumPrice,
                        auction.startedAt,
                        downAuction.dropPrice,
                        downAuction.priceDropInterval
                ))
                .from(auction)
                .leftJoin(downAuction).on(downAuction.id.eq(auction.id))
                .where(
                        searchPredicate(condition),
                        auction.instanceOf(DownAuction.class),
                        downAuction.id.isNotNull(),
                        startPriceCursorAfter(cursor)
                )
                .orderBy(
                        auction.startPrice.desc(),
                        auction.id.desc()
                )
                .limit(limit)
                .fetch();
    }

    @Override
    public List<AuctionListRow> findRowsByPriceSnapshots(
            List<AuctionPriceSnapshot> snapshots,
            LocalDateTime asOf
    ) {
        if (snapshots.isEmpty()) {
            return List.of();
        }
        List<Long> auctionIds = snapshots.stream()
                .map(AuctionPriceSnapshot::auctionId)
                .toList();
        Map<Long, AuctionBidSummary> bidSummaries = bidSummariesByAuctionId(
                auctionIds,
                asOf
        );
        List<AuctionListMetrics> metrics = snapshots.stream()
                .map(snapshot -> {
                    AuctionBidSummary bidSummary = bidSummaries.get(snapshot.auctionId());
                    return new AuctionListMetrics(
                            snapshot.auctionId(),
                            snapshot.currentPrice(),
                            bidSummary != null ? bidSummary.bidCount() : 0L
                    );
                })
                .toList();
        return findRows(metrics);
    }

    private List<AuctionListRow> findRows(List<AuctionListMetrics> metrics) {
        if (metrics.isEmpty()) {
            return List.of();
        }

        List<Long> auctionIds = metrics.stream()
                .map(AuctionListMetrics::auctionId)
                .toList();
        Map<Long, AuctionListDetails> detailsByAuctionId = findDetailsByAuctionId(auctionIds);
        Map<Long, String> thumbnailKeysByAuctionId = findThumbnailKeysByAuctionId(auctionIds);

        return metrics.stream()
                .map(metric -> toRow(
                        detailsByAuctionId.get(metric.auctionId()),
                        metric,
                        thumbnailKeysByAuctionId.get(metric.auctionId())
                ))
                .toList();
    }

    private BooleanExpression minimumPriceCursorAfter(AuctionPriceCursor cursor) {
        if (cursor == null) {
            return null;
        }
        return downAuction.minimumPrice.gt(cursor.priceBound())
                .or(downAuction.minimumPrice.eq(cursor.priceBound())
                        .and(downAuction.id.lt(cursor.auctionId())));
    }

    private BooleanExpression startPriceCursorAfter(AuctionPriceCursor cursor) {
        if (cursor == null) {
            return null;
        }
        return auction.startPrice.lt(cursor.priceBound())
                .or(auction.startPrice.eq(cursor.priceBound())
                        .and(auction.id.lt(cursor.auctionId())));
    }

    private OrderSpecifier<?>[] upPriceOrderBy(AuctionSort sort) {
        return switch (sort) {
            case PRICE_LOW -> new OrderSpecifier<?>[]{
                    auction.currentPrice.asc(),
                    auction.id.desc()
            };
            case PRICE_HIGH -> new OrderSpecifier<?>[]{
                    auction.currentPrice.desc(),
                    auction.id.desc()
            };
            case RECOMMENDED, DEADLINE, LATEST ->
                    throw new IllegalArgumentException("가격 정렬이 아닙니다: " + sort);
        };
    }

    private Map<Long, AuctionBidSummary> bidSummariesByAuctionId(
            List<Long> auctionIds,
            LocalDateTime asOf
    ) {
        return bidRepository.summarizeByAuctionIds(auctionIds, asOf)
                .stream()
                .collect(Collectors.toMap(
                        AuctionBidSummary::auctionId,
                        Function.identity()
                ));
    }

    private List<AuctionListMetrics> findPageByColumnSort(
            AuctionListSearchCondition condition,
            long offset,
            int limit
    ) {
        List<Long> auctionIds = queryFactory
                .select(auction.id)
                .from(auction)
                .where(searchPredicate(condition))
                .orderBy(columnOrderBy(condition.sort()))
                .offset(offset)
                .limit(limit)
                .fetch();
        if (auctionIds.isEmpty()) {
            return List.of();
        }

        Map<Long, AuctionListMetrics> metricsByAuctionId = findMetricsByAuctionId(
                auctionIds,
                condition.asOf()
        );
        return auctionIds.stream()
                .map(auctionId -> {
                    AuctionListMetrics metrics = metricsByAuctionId.get(auctionId);
                    if (metrics == null) {
                        throw new IllegalStateException(
                                "페이지 경매 집계 정보를 조회하지 못했습니다: " + auctionId
                        );
                    }
                    return metrics;
                })
                .toList();
    }

    private Map<Long, AuctionListMetrics> findMetricsByAuctionId(
            List<Long> auctionIds,
            LocalDateTime asOf
    ) {
        AuctionListMetricExpressions expressions = metricExpressions();
        List<AuctionListMetrics> metrics = baseMetricQuery(expressions, asOf)
                .where(auction.id.in(auctionIds))
                .groupBy(expressions.groupByKeys())
                .fetch();

        return metrics.stream()
                .collect(Collectors.toMap(
                        AuctionListMetrics::auctionId,
                        Function.identity()
                ));
    }

    private List<AuctionListMetrics> findPageByAggregateSort(
            AuctionListSearchCondition condition,
            long offset,
            int limit
    ) {
        AuctionListMetricExpressions expressions = metricExpressions();
        return baseMetricQuery(expressions, condition.asOf())
                .where(searchPredicate(condition))
                .groupBy(expressions.groupByKeys())
                .orderBy(aggregateOrderBy(condition.sort(), expressions))
                .offset(offset)
                .limit(limit)
                .fetch();
    }

    private JPAQuery<AuctionListMetrics> baseMetricQuery(
            AuctionListMetricExpressions expressions,
            LocalDateTime asOf
    ) {
        return queryFactory
                .select(new QAuctionListMetrics(
                        auction.id,
                        expressions.currentPrice(),
                        expressions.bidCount()
                ))
                .from(auction)
                .leftJoin(downAuction).on(downAuction.id.eq(auction.id))
                .leftJoin(bid).on(
                        bid.auction.id.eq(auction.id)
                                .and(bid.createdAt.loe(asOf))
                )
                // LocalDateTime constant는 numberTemplate 안에서 Hibernate 7이 VARCHAR로 해석한다.
                .set(PRICE_AS_OF, asOf);
    }

    private Predicate searchPredicate(AuctionListSearchCondition condition) {
        return new BooleanBuilder()
                .and(auction.startedAt.loe(condition.asOf()))
                .and(auction.completedAt.isNull().or(
                        auction.completedAt.gt(condition.asOf())
                ))
                .and(auction.endedAt.gt(condition.asOf()))
                .and(auctionTypeEq(condition.auctionType()))
                .and(titleContains(auction.title, condition.keyword()));
    }

    private Predicate downSearchPredicate(AuctionListSearchCondition condition) {
        return new BooleanBuilder()
                .and(downAuction.startedAt.loe(condition.asOf()))
                .and(downAuction.completedAt.isNull().or(
                        downAuction.completedAt.gt(condition.asOf())
                ))
                .and(downAuction.endedAt.gt(condition.asOf()))
                .and(titleContains(downAuction.title, condition.keyword()));
    }

    private BooleanExpression titleContains(StringExpression title, String keyword) {
        // 제목 검색 조건이 없으면 BooleanBuilder.and(null)에서 무시되도록 null을 반환한다.
        if (keyword == null) {
            return null;
        }
        // 컬럼 collation이 utf8mb4_0900_ai_ci(대소문자 무시)라 LOWER() 없이도
        // 대소문자 구분 없이 매칭된다.
        String pattern = "%" + AuctionListKeywordEscaper.escape(keyword) + "%";
        return title.like(pattern, AuctionListKeywordEscaper.LIKE_ESCAPE);
    }

    private BooleanExpression auctionTypeEq(AuctionType auctionType) {
        // 경매 유형 조건이 없으면 BooleanBuilder.and(null)에서 무시되도록 null을 반환한다.
        if (auctionType == null) {
            return null;
        }
        return switch (auctionType) {
            case UP -> auction.instanceOf(UpAuction.class);
            case DOWN -> auction.instanceOf(DownAuction.class);
        };
    }

    private OrderSpecifier<?>[] columnOrderBy(AuctionSort sort) {
        return switch (sort) {
            case DEADLINE -> new OrderSpecifier<?>[]{
                    auction.endedAt.asc(),
                    auction.id.asc()
            };
            case LATEST -> new OrderSpecifier<?>[]{
                    auction.createdAt.desc(),
                    auction.id.desc()
            };
            case RECOMMENDED, PRICE_LOW, PRICE_HIGH ->
                    throw new IllegalArgumentException("컬럼 정렬이 아닙니다: " + sort);
        };
    }

    private OrderSpecifier<?>[] aggregateOrderBy(
            AuctionSort sort,
            AuctionListMetricExpressions expressions
    ) {
        return switch (sort) {
            case RECOMMENDED -> new OrderSpecifier<?>[]{
                    expressions.bidCount().desc(),
                    auction.id.desc()
            };
            case PRICE_LOW, PRICE_HIGH, DEADLINE, LATEST ->
                    throw new IllegalArgumentException("집계 정렬이 아닙니다: " + sort);
        };
    }

    private AuctionListMetricExpressions metricExpressions() {
        NumberExpression<Long> bidCount = bid.id.count();
        NumberExpression<Long> upCurrentPrice = bid.price.max().coalesce(auction.startPrice);
        NumberExpression<Long> downCurrentPrice = Expressions.numberTemplate(
                Long.class,
                DOWN_CURRENT_PRICE_TEMPLATE,
                downAuction.minimumPrice,
                auction.startPrice,
                auction.startedAt,
                PRICE_AS_OF,
                downAuction.priceDropInterval,
                downAuction.dropPrice
        );
        NumberExpression<Long> currentPrice = Expressions.cases()
                .when(auction.instanceOf(UpAuction.class))
                .then(upCurrentPrice)
                .otherwise(downCurrentPrice);
        return new AuctionListMetricExpressions(
                currentPrice,
                bidCount,
                new Expression<?>[]{
                        auction.id,
                        auction.startPrice,
                        auction.startedAt,
                        downAuction.minimumPrice,
                        downAuction.dropPrice,
                        downAuction.priceDropInterval
                }
        );
    }

    private Map<Long, AuctionListDetails> findDetailsByAuctionId(List<Long> auctionIds) {
        return queryFactory
                .select(new QAuctionListDetails(
                        auction.id,
                        downAuction.id,
                        auction.title,
                        member.nickname,
                        auction.category,
                        auction.startPrice,
                        auction.endedAt,
                        auction.createdAt,
                        auction.status,
                        auction.revision,
                        auction.startedAt,
                        downAuction.minimumPrice,
                        downAuction.dropPrice,
                        downAuction.priceDropInterval
                ))
                .from(auction)
                .join(auction.seller, member)
                .leftJoin(downAuction).on(downAuction.id.eq(auction.id))
                .where(auction.id.in(auctionIds))
                .fetch()
                .stream()
                .collect(Collectors.toMap(AuctionListDetails::auctionId, Function.identity()));
    }

    private Map<Long, String> findThumbnailKeysByAuctionId(List<Long> auctionIds) {
        QImage firstImage = new QImage("firstImage");
        List<Tuple> tuples = queryFactory
                .select(image.auction.id, image.objectKey)
                .from(image)
                .where(
                        image.auction.id.in(auctionIds),
                        image.id.eq(
                                JPAExpressions
                                        .select(firstImage.id.min())
                                        .from(firstImage)
                                        .where(firstImage.auction.id.eq(image.auction.id))
                        )
                )
                .fetch();

        return tuples.stream()
                .collect(Collectors.toMap(
                        tuple -> requireValue(tuple.get(image.auction.id), "auctionId"),
                        tuple -> requireValue(tuple.get(image.objectKey), "thumbnailObjectKey")
                ));
    }

    private AuctionListRow toRow(
            AuctionListDetails details,
            AuctionListMetrics metrics,
            String thumbnailObjectKey
    ) {
        if (details == null) {
            throw new IllegalStateException("페이지 경매 정보를 조회하지 못했습니다: " + metrics.auctionId());
        }
        return details.toRow(metrics, thumbnailObjectKey);
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("경매 목록 조회 결과에 " + fieldName + "이(가) 없습니다.");
        }
        return value;
    }

    private record AuctionListMetricExpressions(
            NumberExpression<Long> currentPrice,
            NumberExpression<Long> bidCount,
            Expression<?>[] groupByKeys
    ) {
    }
}
