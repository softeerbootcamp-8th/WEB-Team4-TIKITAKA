package com.tikitaka.bidwinback.auction.infrastructure;

import com.querydsl.core.BooleanBuilder;
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
import org.hibernate.jpa.HibernateHints;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static final Comparator<AuctionColumnSortCandidate> LATEST_ORDER =
            Comparator.comparing(AuctionColumnSortCandidate::sortAt)
                    .reversed()
                    .thenComparing(
                            Comparator.comparingLong(AuctionColumnSortCandidate::auctionId)
                                    .reversed()
                    );
    private static final Comparator<AuctionColumnSortCandidate> DEADLINE_ORDER =
            Comparator.comparing(AuctionColumnSortCandidate::sortAt)
                    .thenComparingLong(AuctionColumnSortCandidate::auctionId);
    private static final Comparator<DownAuctionPriceCandidateDetails> DOWN_START_PRICE_ORDER =
            Comparator.comparingLong(DownAuctionPriceCandidateDetails::startPrice)
                    .reversed()
                    .thenComparing(
                            Comparator.comparingLong(
                                    DownAuctionPriceCandidateDetails::auctionId
                            ).reversed()
                    );

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
                .setHint(
                        HibernateHints.HINT_QUERY_DATABASE,
                        "idx_auction_count"
                )
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
        return queryFactory
                .select(new QUpAuctionPriceSnapshotDetails(
                        auction.id,
                        auction.currentPrice
                ))
                .from(auction)
                .where(
                        searchPredicate(condition),
                        auction.instanceOf(UpAuction.class)
                )
                .orderBy(upPriceOrderBy(condition.sort()))
                .limit(limit)
                .fetch()
                .stream()
                .map(UpAuctionPriceSnapshotDetails::toSnapshot)
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
        return toDownPriceCandidates(details);
    }

    @Override
    public List<DownAuctionPriceCandidate> findRemainingDownPriceCandidatesAtBound(
            AuctionListSearchCondition condition,
            AuctionPriceCursor cursor
    ) {
        List<DownAuctionPriceCandidateDetails> details = switch (condition.sort()) {
            case PRICE_LOW -> findRemainingDownCandidatesAtMinimumPrice(condition, cursor);
            case PRICE_HIGH -> findRemainingDownCandidatesAtStartPrice(condition, cursor);
            case RECOMMENDED, DEADLINE, LATEST ->
                    throw new IllegalArgumentException("가격 정렬이 아닙니다: " + condition.sort());
        };
        return toDownPriceCandidates(details);
    }

    private List<DownAuctionPriceCandidateDetails> findDownCandidatesByMinimumPrice(
            AuctionListSearchCondition condition,
            AuctionPriceCursor cursor,
            int limit
    ) {
        // 최저가는 하위 테이블에만 있으므로 down_auction의 경계 인덱스부터 읽어야
        // 활성 경매 필터를 위한 auction 조인 전에 LIMIT을 향해 순서대로 스캔할 수 있다.
        List<Long> pageCandidateIds = queryFactory
                .select(downAuction.id)
                .from(downAuction)
                .where(
                        downAuctionSearchPredicate(condition),
                        minimumPriceCursorAfter(cursor)
                )
                .orderBy(
                        downAuction.minimumPrice.asc(),
                        downAuction.id.desc()
                )
                .limit(limit)
                .setHint(HibernateHints.HINT_QUERY_DATABASE,
                        "idx_down_auction_minimum_price_id")
                .fetch();

        return candidateQueryFromDownAuction()
                .where(
                        downAuction.id.in(pageCandidateIds)
                )
                .orderBy(
                        downAuction.minimumPrice.asc(),
                        downAuction.id.desc()
                )
                .fetch();
    }

    private List<DownAuctionPriceCandidateDetails> findRemainingDownCandidatesAtMinimumPrice(
            AuctionListSearchCondition condition,
            AuctionPriceCursor cursor
    ) {
        return candidateQueryFromDownAuction()
                .where(
                        downAuctionSearchPredicate(condition),
                        downAuction.minimumPrice.eq(cursor.priceBound()),
                        downAuction.id.lt(cursor.auctionId())
                )
                .orderBy(downAuction.id.desc())
                .fetch();
    }

    // down_auction만 읽으므로 최저가 인덱스가 스캔을 주도한다.
    private JPAQuery<DownAuctionPriceCandidateDetails> candidateQueryFromDownAuction() {
        return queryFactory
                .select(new QDownAuctionPriceCandidateDetails(
                        downAuction.id,
                        downAuction.startPrice,
                        downAuction.minimumPrice,
                        downAuction.startedAt,
                        downAuction.dropPrice,
                        downAuction.priceDropInterval
                ))
                .from(downAuction);
    }

    private List<DownAuctionPriceCandidateDetails> findDownCandidatesByStartPrice(
            AuctionListSearchCondition condition,
            AuctionPriceCursor cursor,
            int limit
    ) {
        return mergeAcrossCompletionStates(
                condition,
                limit,
                (completedAtPredicate, branchLimit) -> findDownStartPriceCandidatesInBranch(
                        condition,
                        cursor,
                        branchLimit,
                        completedAtPredicate
                ),
                DOWN_START_PRICE_ORDER
        );
    }

    private List<DownAuctionPriceCandidateDetails> findDownStartPriceCandidatesInBranch(
            AuctionListSearchCondition condition,
            AuctionPriceCursor cursor,
            long branchLimit,
            Predicate completedAtPredicate
    ) {
        return candidateQueryFromAuction(
                searchPredicate(condition, completedAtPredicate)
        )
                .where(
                        startPriceCursorAfter(cursor)
                )
                .orderBy(
                        auction.startPrice.desc(),
                        auction.id.desc()
                )
                .limit(branchLimit)
                .fetch();
    }

    private List<DownAuctionPriceCandidateDetails> findRemainingDownCandidatesAtStartPrice(
            AuctionListSearchCondition condition,
            AuctionPriceCursor cursor
    ) {
        return candidateQueryFromAuction(searchPredicate(condition))
                .where(
                        auction.startPrice.eq(cursor.priceBound()),
                        auction.id.lt(cursor.auctionId())
                )
                .orderBy(auction.id.desc())
                .fetch();
    }

    // auction을 읽고 down_auction을 조인하므로 시작가 인덱스가 스캔을 주도한다.
    private JPAQuery<DownAuctionPriceCandidateDetails> candidateQueryFromAuction(
            Predicate searchPredicate
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
                .innerJoin(downAuction).on(downAuction.id.eq(auction.id))
                .where(
                        searchPredicate,
                        auction.instanceOf(DownAuction.class)
                );
    }

    private List<DownAuctionPriceCandidate> toDownPriceCandidates(
            List<DownAuctionPriceCandidateDetails> details
    ) {
        return details.stream()
                .map(DownAuctionPriceCandidateDetails::toCandidate)
                .toList();
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
        Map<Long, AuctionBidSummary> bidSummaries = findBidSummariesByAuctionId(
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

    // 최저가 오름차순이므로 커서 다음 페이지는 더 큰 최저가 쪽이다.
    private BooleanExpression minimumPriceCursorAfter(AuctionPriceCursor cursor) {
        if (cursor == null) {
            return null;
        }
        return downAuction.minimumPrice.gt(cursor.priceBound())
                .or(downAuction.minimumPrice.eq(cursor.priceBound())
                        .and(downAuction.id.lt(cursor.auctionId())));
    }

    // 시작가 내림차순이므로 커서 다음 페이지는 더 작은 시작가 쪽이다.
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

    private Map<Long, AuctionBidSummary> findBidSummariesByAuctionId(
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
        ColumnSortSpec sortSpec = columnSortSpec(condition.sort());
        if (condition.auctionType() == null) {
            return findPageByColumnSortAcrossTypes(
                    condition,
                    offset,
                    limit,
                    sortSpec
            );
        }

        // 정렬 인덱스에서 페이지 후보만 먼저 고르고, 집계 쿼리는 선택된 ID에만 수행한다.
        List<Long> auctionIds = mergeAcrossCompletionStates(
                condition,
                offset,
                limit,
                (completedAtPredicate, branchLimit) -> findColumnSortCandidatesInBranch(
                        condition,
                        completedAtPredicate,
                        branchLimit,
                        sortSpec
                ),
                sortSpec.comparator()
        )
                .stream()
                .map(AuctionColumnSortCandidate::auctionId)
                .toList();
        return findMetricsInPageOrder(auctionIds, condition.asOf());
    }

    private List<AuctionListMetrics> findPageByColumnSortAcrossTypes(
            AuctionListSearchCondition condition,
            long offset,
            int limit,
            ColumnSortSpec sortSpec
    ) {
        // auction_type이 정해지지 않으면 유형별 인덱스 결과를 한 번에 정렬할 수 없어 OFFSET을 사용한다.
        List<Long> auctionIds = queryFactory
                .select(auction.id)
                .from(auction)
                .where(searchPredicate(condition))
                .orderBy(sortSpec.sortOrder(), sortSpec.idOrder())
                .offset(offset)
                .limit(limit)
                .fetch();
        return findMetricsInPageOrder(auctionIds, condition.asOf());
    }

    private List<AuctionColumnSortCandidate> findColumnSortCandidatesInBranch(
            AuctionListSearchCondition condition,
            Predicate completedAtPredicate,
            long branchLimit,
            ColumnSortSpec sortSpec
    ) {
        return queryFactory
                .select(new QAuctionColumnSortCandidate(
                        auction.id,
                        sortSpec.sortExpression()
                ))
                .from(auction)
                .where(searchPredicate(condition, completedAtPredicate))
                .orderBy(sortSpec.sortOrder(), sortSpec.idOrder())
                .limit(branchLimit)
                // completed_at 분기별로 정렬에 맞는 복합 인덱스를 사용한다.
                .setHint(
                        HibernateHints.HINT_QUERY_DATABASE,
                        sortSpec.indexHint()
                )
                .fetch();
    }

    private <T> List<T> mergeAcrossCompletionStates(
            AuctionListSearchCondition condition,
            int limit,
            BiFunction<Predicate, Long, List<T>> branchQuery,
            Comparator<T> comparator
    ) {
        return mergeAcrossCompletionStates(condition, 0, limit, branchQuery, comparator);
    }

    private <T> List<T> mergeAcrossCompletionStates(
            AuctionListSearchCondition condition,
            long offset,
            int limit,
            BiFunction<Predicate, Long, List<T>> branchQuery,
            Comparator<T> comparator
    ) {
        // 활성 조건의 OR을 인덱스로 처리하기 위해 completed_at IS NULL / > asOf로 나눈다.
        // QueryDSL JPA가 UNION ALL을 지원하지 않으므로 각 분기에서 offset + limit만큼 읽고 병합한다.
        long branchLimit = Math.addExact(offset, limit);
        List<T> notYetCompleted = branchQuery.apply(
                auction.completedAt.isNull(),
                branchLimit
        );
        List<T> completedAfterAsOf = branchQuery.apply(
                auction.completedAt.gt(condition.asOf()),
                branchLimit
        );

        return Stream.concat(notYetCompleted.stream(), completedAfterAsOf.stream())
                .sorted(comparator)
                .skip(offset)
                .limit(limit)
                .toList();
    }

    private List<AuctionListMetrics> findMetricsInPageOrder(
            List<Long> auctionIds,
            LocalDateTime asOf
    ) {
        if (auctionIds.isEmpty()) {
            return List.of();
        }

        Map<Long, AuctionListMetrics> metricsByAuctionId = findMetricsByAuctionId(
                auctionIds,
                asOf
        );
        // IN 조회 결과는 순서를 보장하지 않으므로 후보 쿼리가 정한 페이지 순서로 복원한다.
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
        return searchPredicate(
                condition,
                auction.completedAt.isNull()
                        .or(auction.completedAt.gt(condition.asOf()))
        );
    }

    private Predicate searchPredicate(
            AuctionListSearchCondition condition,
            Predicate completedAtPredicate
    ) {
        return new BooleanBuilder()
                .and(auction.startedAt.loe(condition.asOf()))
                .and(completedAtPredicate)
                .and(auction.endedAt.gt(condition.asOf()))
                .and(auctionTypeEq(condition.auctionType()))
                .and(titleContains(auction.title, condition.keyword()));
    }

    private Predicate downAuctionSearchPredicate(AuctionListSearchCondition condition) {
        return new BooleanBuilder()
                .and(downAuction.startedAt.loe(condition.asOf()))
                .and(downAuction.completedAt.isNull()
                        .or(downAuction.completedAt.gt(condition.asOf())))
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

    private ColumnSortSpec columnSortSpec(AuctionSort sort) {
        return switch (sort) {
            case DEADLINE -> new ColumnSortSpec(
                    auction.endedAt,
                    auction.endedAt.asc(),
                    auction.id.asc(),
                    DEADLINE_ORDER,
                    "idx_auction_snapshot_deadline"
            );
            case LATEST -> new ColumnSortSpec(
                    auction.createdAt,
                    auction.createdAt.desc(),
                    auction.id.desc(),
                    LATEST_ORDER,
                    "idx_auction_snapshot_latest"
            );
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
        return queryFactory
                .select(new QAuctionThumbnailDetails(
                        image.auction.id,
                        image.objectKey
                ))
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
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        AuctionThumbnailDetails::auctionId,
                        AuctionThumbnailDetails::objectKey
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

    private record AuctionListMetricExpressions(
            NumberExpression<Long> currentPrice,
            NumberExpression<Long> bidCount,
            Expression<?>[] groupByKeys
    ) {
    }

    // DB의 분기별 정렬과 애플리케이션의 병합 정렬은 같은 키와 방향을 사용해야 한다.
    private record ColumnSortSpec(
            Expression<LocalDateTime> sortExpression,
            OrderSpecifier<?> sortOrder,
            OrderSpecifier<?> idOrder,
            Comparator<AuctionColumnSortCandidate> comparator,
            String indexHint
    ) {
    }

}
