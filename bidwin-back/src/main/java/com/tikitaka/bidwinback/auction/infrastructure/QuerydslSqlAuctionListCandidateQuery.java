package com.tikitaka.bidwinback.auction.infrastructure;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.JoinFlag;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.SubQueryExpression;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.sql.JPASQLQuery;
import com.querydsl.sql.MySQLTemplates;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SQLTemplates;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionListStatusFilter;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListSearchCondition;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;

@Repository
class QuerydslSqlAuctionListCandidateQuery {

    private static final SQLTemplates MYSQL_TEMPLATES = MySQLTemplates.builder().build();
    private static final AuctionTable AUCTION = new AuctionTable("auction");
    private static final PathBuilder<Object> CANDIDATE = new PathBuilder<>(
            Object.class,
            "candidate"
    );
    private static final NumberPath<Long> CANDIDATE_ID = CANDIDATE.getNumber(
            "auction_id",
            Long.class
    );
    private static final DateTimePath<LocalDateTime> CANDIDATE_SORT_AT = CANDIDATE.getDateTime(
            "sort_at",
            LocalDateTime.class
    );
    private static final NumberPath<Long> CANDIDATE_BID_COUNT = CANDIDATE.getNumber(
            "bid_count",
            Long.class
    );

    private final EntityManager entityManager;

    QuerydslSqlAuctionListCandidateQuery(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    List<AuctionColumnSortCandidate> findColumnSortCandidates(
            AuctionListSearchCondition condition,
            long offset,
            int limit
    ) {
        ColumnSortSpec sortSpec = columnSortSpec(condition.sort());
        long branchLimit = Math.addExact(offset, limit);
        List<SubQueryExpression<Tuple>> branches = new ArrayList<>();

        for (AuctionType auctionType : auctionTypes(condition.auctionType())) {
            for (BooleanExpression statusPredicate : columnStatusBranchPredicates(condition)) {
                branches.add(candidateBranch(
                        condition,
                        auctionType,
                        statusPredicate,
                        sortSpec,
                        branchLimit
                ));
            }
        }

        @SuppressWarnings("unchecked")
        SubQueryExpression<Tuple>[] branchArray = branches.toArray(SubQueryExpression[]::new);
        JPASQLQuery<Tuple> query = new JPASQLQuery<>(entityManager, MYSQL_TEMPLATES);
        query.unionAll(CANDIDATE, branchArray);

        return query
                .select(CANDIDATE_ID, CANDIDATE_SORT_AT)
                .orderBy(sortSpec.outerSortOrder(), sortSpec.outerIdOrder())
                .offset(offset)
                .limit(limit)
                .fetch()
                .stream()
                .map(row -> new AuctionColumnSortCandidate(
                        row.get(CANDIDATE_ID),
                        row.get(CANDIDATE_SORT_AT)
                ))
                .toList();
    }

    List<AuctionRecommendedCandidate> findRecommendedCandidates(
            AuctionListSearchCondition condition,
            long offset,
            int limit
    ) {
        long branchLimit = Math.addExact(offset, limit);
        List<SubQueryExpression<Tuple>> branches = new ArrayList<>();

        for (AuctionType auctionType : auctionTypes(condition.auctionType())) {
            for (BooleanExpression statusPredicate : recommendedStatusBranchPredicates(condition)) {
                branches.add(recommendedCandidateBranch(
                        condition,
                        auctionType,
                        statusPredicate,
                        branchLimit
                ));
            }
        }

        @SuppressWarnings("unchecked")
        SubQueryExpression<Tuple>[] branchArray = branches.toArray(SubQueryExpression[]::new);
        JPASQLQuery<Tuple> query = new JPASQLQuery<>(entityManager, MYSQL_TEMPLATES);
        query.unionAll(CANDIDATE, branchArray);

        return query
                .select(CANDIDATE_ID, CANDIDATE_BID_COUNT)
                .orderBy(CANDIDATE_BID_COUNT.desc(), CANDIDATE_ID.desc())
                .offset(offset)
                .limit(limit)
                .fetch()
                .stream()
                .map(row -> new AuctionRecommendedCandidate(
                        row.get(CANDIDATE_ID),
                        row.get(CANDIDATE_BID_COUNT)
                ))
                .toList();
    }

    private JPASQLQuery<Tuple> candidateBranch(
            AuctionListSearchCondition condition,
            AuctionType auctionType,
            BooleanExpression statusPredicate,
            ColumnSortSpec sortSpec,
            long branchLimit
    ) {
        return new JPASQLQuery<>(entityManager, MYSQL_TEMPLATES)
                .select(
                        AUCTION.id.as("auction_id"),
                        sortSpec.sortPath().as("sort_at")
                )
                .from(AUCTION)
                .addJoinFlag(
                        " use index (" + sortSpec.indexHint() + ")",
                        JoinFlag.Position.END
                )
                .where(searchPredicate(condition, auctionType, statusPredicate))
                .orderBy(sortSpec.branchSortOrder(), sortSpec.branchIdOrder())
                .limit(branchLimit);
    }

    private JPASQLQuery<Tuple> recommendedCandidateBranch(
            AuctionListSearchCondition condition,
            AuctionType auctionType,
            BooleanExpression statusPredicate,
            long branchLimit
    ) {
        return new JPASQLQuery<>(entityManager, MYSQL_TEMPLATES)
                .select(
                        AUCTION.id.as("auction_id"),
                        AUCTION.bidCount.as("bid_count")
                )
                .from(AUCTION)
                .addJoinFlag(
                        " use index (idx_auction_recommended)",
                        JoinFlag.Position.END
                )
                .where(searchPredicate(condition, auctionType, statusPredicate))
                .orderBy(AUCTION.bidCount.desc(), AUCTION.id.desc())
                .limit(branchLimit);
    }

    private BooleanBuilder searchPredicate(
            AuctionListSearchCondition condition,
            AuctionType auctionType,
            BooleanExpression statusPredicate
    ) {
        return new BooleanBuilder()
                .and(AUCTION.startedAt.loe(condition.asOf()))
                .and(statusPredicate)
                .and(AUCTION.auctionType.eq(auctionType.name()))
                .and(categoryEq(condition))
                .and(titleContains(condition.keyword()));
    }

    private BooleanExpression categoryEq(AuctionListSearchCondition condition) {
        return condition.category() != null
                ? AUCTION.category.eq(condition.category().name())
                : null;
    }

    private BooleanExpression titleContains(String keyword) {
        if (keyword == null) {
            return null;
        }
        String pattern = "%" + AuctionListKeywordEscaper.escape(keyword) + "%";
        return AUCTION.title.like(pattern, AuctionListKeywordEscaper.LIKE_ESCAPE);
    }

    private List<AuctionType> auctionTypes(AuctionType auctionType) {
        return auctionType != null
                ? List.of(auctionType)
                : List.of(AuctionType.UP, AuctionType.DOWN);
    }

    private List<BooleanExpression> columnStatusBranchPredicates(
            AuctionListSearchCondition condition
    ) {
        AuctionListStatusFilter status = condition.status() != null
                ? condition.status()
                : AuctionListStatusFilter.ACTIVE;
        return status == AuctionListStatusFilter.ENDED
                ? List.of(
                        AUCTION.completedAt.loe(condition.asOf()),
                        AUCTION.completedAt.isNull()
                                .and(AUCTION.endedAt.loe(condition.asOf())),
                        AUCTION.completedAt.gt(condition.asOf())
                                .and(AUCTION.endedAt.loe(condition.asOf()))
                )
                : List.of(
                        AUCTION.completedAt.isNull()
                                .and(AUCTION.endedAt.gt(condition.asOf())),
                        AUCTION.completedAt.gt(condition.asOf())
                                .and(AUCTION.endedAt.gt(condition.asOf()))
                );
    }

    private List<BooleanExpression> recommendedStatusBranchPredicates(
            AuctionListSearchCondition condition
    ) {
        AuctionListStatusFilter status = condition.status() != null
                ? condition.status()
                : AuctionListStatusFilter.ACTIVE;
        return status == AuctionListStatusFilter.ENDED
                ? List.of(
                        AUCTION.completedAt.isNotNull(),
                        AUCTION.completedAt.isNull()
                                .and(AUCTION.endedAt.loe(condition.asOf()))
                )
                : List.of(
                        AUCTION.completedAt.isNull()
                                .and(AUCTION.endedAt.gt(condition.asOf()))
                );
    }

    private ColumnSortSpec columnSortSpec(AuctionSort sort) {
        return switch (sort) {
            case DEADLINE -> new ColumnSortSpec(
                    AUCTION.endedAt,
                    AUCTION.endedAt.asc(),
                    AUCTION.id.asc(),
                    CANDIDATE_SORT_AT.asc(),
                    CANDIDATE_ID.asc(),
                    "idx_auction_snapshot_deadline"
            );
            case LATEST -> new ColumnSortSpec(
                    AUCTION.createdAt,
                    AUCTION.createdAt.desc(),
                    AUCTION.id.desc(),
                    CANDIDATE_SORT_AT.desc(),
                    CANDIDATE_ID.desc(),
                    "idx_auction_snapshot_latest"
            );
            case RECOMMENDED, PRICE_LOW, PRICE_HIGH ->
                    throw new IllegalArgumentException("컬럼 정렬이 아닙니다: " + sort);
        };
    }

    private record ColumnSortSpec(
            DateTimePath<LocalDateTime> sortPath,
            OrderSpecifier<LocalDateTime> branchSortOrder,
            OrderSpecifier<Long> branchIdOrder,
            OrderSpecifier<LocalDateTime> outerSortOrder,
            OrderSpecifier<Long> outerIdOrder,
            String indexHint
    ) {
    }

    private static final class AuctionTable extends RelationalPathBase<Object> {

        private final NumberPath<Long> id = createNumber("id", Long.class);
        private final StringPath auctionType = createString("auction_type");
        private final StringPath title = createString("title");
        private final StringPath category = createString("category");
        private final NumberPath<Long> bidCount = createNumber("bid_count", Long.class);
        private final DateTimePath<LocalDateTime> startedAt = createDateTime(
                "started_at",
                LocalDateTime.class
        );
        private final DateTimePath<LocalDateTime> endedAt = createDateTime(
                "ended_at",
                LocalDateTime.class
        );
        private final DateTimePath<LocalDateTime> completedAt = createDateTime(
                "completed_at",
                LocalDateTime.class
        );
        private final DateTimePath<LocalDateTime> createdAt = createDateTime(
                "created_at",
                LocalDateTime.class
        );

        private AuctionTable(String variable) {
            super(Object.class, forVariable(variable), null, "auction");
        }
    }
}
