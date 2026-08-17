package com.tikitaka.bidwinback.auction.load;

import com.tikitaka.bidwinback.auction.application.AuctionClosingBatchProcessor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.storage.s3.bucket=test-bucket",
        "app.auction.closing-interval=24h",
        "logging.level.root=WARN",
        "logging.level.com.tikitaka.bidwinback.auction.application."
                + "AuctionClosingBatchProcessor=OFF",
        "spring.main.banner-mode=off",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
@Tag("load")
class AuctionClosingLoadScenario {

    private static final long SELLER_ID = 1L;
    private static final long INITIAL_POINT = 2_000_000L;
    private static final long START_PRICE = 100_000L;
    private static final long FINAL_PRICE = 101_000L;
    private static final int JDBC_BATCH_SIZE = 1_000;
    private static final long MAX_DEPOSIT_ROWS = 200_000L;
    private static final Path REPORT_PATH = Path.of(
            "build", "reports", "auction-closing-load", "result.json");

    private static final String INSERT_MEMBER_SQL = """
            INSERT INTO member (
                id, name, phone_number, nickname, email, password,
                total_point, profile_object_key, status, locked_point,
                auth_version, created_at, last_modified_at
            )
            VALUES (
                ?, '부하테스트', '01012345678', ?, ?, 'encoded-password',
                ?, 'profiles/default-profile.png', 'ACTIVE', ?,
                0, NOW(6), NOW(6)
            )
            """;

    private static final String INSERT_AUCTION_SQL = """
            INSERT INTO auction (
                id, seller_id, auction_type, title, description, status,
                category, start_price, current_price, bid_count, sealed_bid_count,
                current_bidder_id, sealed_top_price, sealed_top_bidder_id,
                ended_at, started_at, completed_at, revision,
                trade_type, contact, created_at, last_modified_at
            )
            VALUES (
                ?, ?, 'UP', ?, '경매 마감 부하테스트', 'BID_ONGOING',
                'HOUSEHOLD', ?, ?, 1, 0,
                ?, NULL, NULL,
                DATE_SUB(NOW(6), INTERVAL 1 MINUTE),
                DATE_SUB(NOW(6), INTERVAL 2 MINUTE), NULL, 0,
                'DELIVERY', '01012345678', NOW(6), NOW(6)
            )
            """;

    private static final String INSERT_UP_AUCTION_SQL = """
            INSERT INTO up_auction (auction_id, buy_now_price)
            VALUES (?, NULL)
            """;

    private static final String INSERT_DEPOSIT_SQL = """
            INSERT INTO auction_deposit (
                id, member_id, auction_id, reserved_amount,
                status, created_at, last_modified_at
            )
            VALUES (?, ?, ?, ?, 'HELD', NOW(6), NOW(6))
            """;

    @Autowired
    private AuctionClosingBatchProcessor batchProcessor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Value("${app.auction.closing-batch-size}")
    private int closingBatchSize;

    @Test
    void 정상_배치와_오류가_포함된_배치의_처리_결과를_비교한다() throws IOException {
        requireIsolatedEmptyDatabase();
        LoadConfig config = LoadConfig.fromEnvironment(closingBatchSize);

        ScenarioResult baseline = executeScenario(config, false);
        ScenarioResult withFailure = executeScenario(config, true);

        verifyBaseline(config, baseline);
        verifyFailureScenario(config, withFailure);
        writeReport(config, baseline, withFailure);
        printReport(config, baseline, withFailure);
    }

    private ScenarioResult executeScenario(LoadConfig config, boolean injectFailure) {
        try {
            seed(config, injectFailure);
            long startedAt = System.nanoTime();
            batchProcessor.closeEndedAuctions();
            long elapsedNanos = System.nanoTime() - startedAt;
            return snapshot(config, injectFailure, elapsedNanos);
        } finally {
            cleanup();
        }
    }

    private void seed(LoadConfig config, boolean injectFailure) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            List<Object[]> memberRows = new ArrayList<>(config.memberRows());
            List<Object[]> auctionRows = new ArrayList<>(config.auctionCount());
            List<Object[]> upAuctionRows = new ArrayList<>(config.auctionCount());
            List<Object[]> depositRows = new ArrayList<>(config.depositRows());

            memberRows.add(memberRow(
                    SELLER_ID,
                    "loadseller",
                    "load-seller@example.com",
                    INITIAL_POINT,
                    0L
            ));

            long depositId = 1L;
            for (int auctionOffset = 0; auctionOffset < config.auctionCount(); auctionOffset++) {
                long auctionId = auctionOffset + 1L;
                long winnerId = memberId(config, auctionOffset, 0);
                memberRows.add(participantRow(winnerId, config.depositAmount()));
                auctionRows.add(new Object[]{
                        auctionId,
                        SELLER_ID,
                        "load-" + auctionId,
                        START_PRICE,
                        FINAL_PRICE,
                        winnerId
                });
                upAuctionRows.add(new Object[]{auctionId});
                depositRows.add(new Object[]{
                        depositId++, winnerId, auctionId, config.depositAmount()
                });

                for (int loserOffset = 0;
                     loserOffset < config.losersPerAuction();
                     loserOffset++) {
                    long loserId = memberId(config, auctionOffset, loserOffset + 1);
                    boolean poisoned = injectFailure
                            && auctionId == config.poisonAuctionId()
                            && loserOffset == 0;
                    long lockedPoint = poisoned
                            ? config.depositAmount() - 1L
                            : config.depositAmount();
                    memberRows.add(participantRow(loserId, lockedPoint));
                    depositRows.add(new Object[]{
                            depositId++, loserId, auctionId, config.depositAmount()
                    });
                }
            }

            batchUpdate(INSERT_MEMBER_SQL, memberRows);
            batchUpdate(INSERT_AUCTION_SQL, auctionRows);
            batchUpdate(INSERT_UP_AUCTION_SQL, upAuctionRows);
            batchUpdate(INSERT_DEPOSIT_SQL, depositRows);
        });
    }

    private Object[] participantRow(long memberId, long lockedPoint) {
        return memberRow(
                memberId,
                "l" + Long.toString(memberId, 36),
                "load-" + memberId + "@example.com",
                Math.subtractExact(INITIAL_POINT, lockedPoint),
                lockedPoint
        );
    }

    private Object[] memberRow(
            long memberId,
            String nickname,
            String email,
            long totalPoint,
            long lockedPoint
    ) {
        return new Object[]{memberId, nickname, email, totalPoint, lockedPoint};
    }

    private long memberId(LoadConfig config, int auctionOffset, int participantOffset) {
        return 2L
                + ((long) auctionOffset * (config.losersPerAuction() + 1L))
                + participantOffset;
    }

    private void batchUpdate(String sql, List<Object[]> rows) {
        for (int from = 0; from < rows.size(); from += JDBC_BATCH_SIZE) {
            int to = Math.min(from + JDBC_BATCH_SIZE, rows.size());
            jdbcTemplate.batchUpdate(sql, rows.subList(from, to));
        }
    }

    private ScenarioResult snapshot(
            LoadConfig config,
            boolean injectFailure,
            long elapsedNanos
    ) {
        long completedAuctions = count(
                "SELECT COUNT(*) FROM auction WHERE status = 'COMPLETED'");
        long pendingAuctions = count(
                "SELECT COUNT(*) FROM auction WHERE status = 'BID_ONGOING'");
        long trades = count("SELECT COUNT(*) FROM auction_trade");
        long refundedDeposits = count(
                "SELECT COUNT(*) FROM auction_deposit WHERE status = 'REFUNDED'");
        long heldDeposits = count(
                "SELECT COUNT(*) FROM auction_deposit WHERE status = 'HELD'");
        String poisonAuctionStatus = injectFailure
                ? jdbcTemplate.queryForObject(
                        "SELECT status FROM auction WHERE id = ?",
                        String.class,
                        config.poisonAuctionId()
                )
                : null;
        verifyPointInvariants(config, injectFailure);

        return new ScenarioResult(
                injectFailure ? "failure-injected" : "baseline",
                Math.max(1L, elapsedNanos / 1_000_000L),
                completedAuctions,
                pendingAuctions,
                trades,
                refundedDeposits,
                heldDeposits,
                poisonAuctionStatus
        );
    }

    private void verifyPointInvariants(LoadConfig config, boolean injectFailure) {
        long invalidRefundedPoints = count("""
                SELECT COUNT(*)
                FROM auction_deposit deposit
                JOIN member ON member.id = deposit.member_id
                WHERE deposit.status = 'REFUNDED'
                  AND (member.total_point <> ? OR member.locked_point <> 0)
                """, INITIAL_POINT);
        long settledWinnerDeposits = count("""
                SELECT COUNT(*)
                FROM auction_deposit deposit
                JOIN auction ON auction.id = deposit.auction_id
                WHERE deposit.member_id = auction.current_bidder_id
                  AND deposit.status <> 'HELD'
                """);

        assertThat(invalidRefundedPoints).isZero();
        assertThat(settledWinnerDeposits).isZero();

        if (injectFailure) {
            long poisonMemberId = memberId(
                    config,
                    Math.toIntExact(config.poisonAuctionId() - 1L),
                    1
            );
            Object[] points = jdbcTemplate.queryForObject(
                    "SELECT total_point, locked_point FROM member WHERE id = ?",
                    (resultSet, rowNumber) -> new Object[]{
                            resultSet.getLong("total_point"),
                            resultSet.getLong("locked_point")
                    },
                    poisonMemberId
            );
            assertThat(points).isNotNull();
            assertThat(((Number) points[0]).longValue())
                    .isEqualTo(INITIAL_POINT - (config.depositAmount() - 1L));
            assertThat(((Number) points[1]).longValue())
                    .isEqualTo(config.depositAmount() - 1L);
        }
    }

    private long count(String sql, Object... arguments) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        if (count == null) {
            throw new IllegalStateException("부하테스트 결과 건수를 조회하지 못했습니다.");
        }
        return count;
    }

    private void verifyBaseline(LoadConfig config, ScenarioResult result) {
        long expectedRefunds = (long) config.auctionCount() * config.losersPerAuction();
        assertThat(result.completedAuctions()).isEqualTo(config.auctionCount());
        assertThat(result.pendingAuctions()).isZero();
        assertThat(result.trades()).isEqualTo(config.auctionCount());
        assertThat(result.refundedDeposits()).isEqualTo(expectedRefunds);
        assertThat(result.heldDeposits()).isEqualTo(config.auctionCount());
    }

    private void verifyFailureScenario(LoadConfig config, ScenarioResult result) {
        long expectedRefunds = result.completedAuctions() * config.losersPerAuction();
        long expectedHeld = config.auctionCount()
                + (result.pendingAuctions() * config.losersPerAuction());

        assertThat(result.poisonAuctionStatus()).isEqualTo("BID_ONGOING");
        assertThat(result.completedAuctions()).isEqualTo(config.auctionCount() - 1L);
        assertThat(result.pendingAuctions()).isEqualTo(1L);
        assertThat(result.completedAuctions() + result.pendingAuctions())
                .isEqualTo(config.auctionCount());
        assertThat(result.trades()).isEqualTo(result.completedAuctions());
        assertThat(result.refundedDeposits()).isEqualTo(expectedRefunds);
        assertThat(result.heldDeposits()).isEqualTo(expectedHeld);
    }

    private void cleanup() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM auction_trade");
            jdbcTemplate.update("DELETE FROM auction_deposit");
            jdbcTemplate.update("DELETE FROM up_auction");
            jdbcTemplate.update("DELETE FROM auction");
            jdbcTemplate.update("DELETE FROM member");
        });
    }

    private void requireIsolatedEmptyDatabase() {
        String databaseName = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> connection.getCatalog());
        if (databaseName == null || !databaseName.matches("bidwin_load_[A-Za-z0-9_]+")) {
            throw new IllegalStateException(
                    "부하테스트는 bidwin_load_ 접두사의 격리 DB에서만 실행할 수 있습니다. database="
                            + databaseName
            );
        }
        if (count("SELECT COUNT(*) FROM auction") != 0L
                || count("SELECT COUNT(*) FROM member") != 0L) {
            throw new IllegalStateException(
                    "부하테스트 fixture 테이블이 비어 있지 않습니다: " + databaseName);
        }
    }

    private void writeReport(
            LoadConfig config,
            ScenarioResult baseline,
            ScenarioResult withFailure
    ) throws IOException {
        Files.createDirectories(REPORT_PATH.getParent());
        String json = """
                {
                  "generatedAt": "%s",
                  "config": {
                    "auctionCount": %d,
                    "losersPerAuction": %d,
                    "depositAmount": %d,
                    "closingBatchSize": %d,
                    "poisonAuctionId": %d
                  },
                  "baseline": %s,
                  "failureInjected": %s,
                  "healthyAuctionsBlocked": %d
                }
                """.formatted(
                Instant.now(),
                config.auctionCount(),
                config.losersPerAuction(),
                config.depositAmount(),
                config.closingBatchSize(),
                config.poisonAuctionId(),
                baseline.toJson(),
                withFailure.toJson(),
                Math.max(0L, withFailure.pendingAuctions() - 1L)
        );
        Files.writeString(REPORT_PATH, json, StandardCharsets.UTF_8);
    }

    private void printReport(
            LoadConfig config,
            ScenarioResult baseline,
            ScenarioResult withFailure
    ) {
        long healthyAuctions = config.auctionCount() - 1L;
        long blockedHealthyAuctions = Math.max(0L, withFailure.pendingAuctions() - 1L);
        double healthyCompletionRate = healthyAuctions == 0L
                ? 0.0
                : (withFailure.completedAuctions() * 100.0) / healthyAuctions;

        System.out.printf(Locale.ROOT, """

                === Auction closing load result ===
                config: auctions=%d, losersPerAuction=%d, batchSize=%d
                baseline: durationMs=%d, completed=%d, endToEndThroughput=%.2f auctions/s
                failure-injected: durationMs=%d, completed=%d, pending=%d
                failure-isolation: healthyCompletionRate=%.2f%%, healthyAuctionsBlocked=%d
                report: %s

                """,
                config.auctionCount(),
                config.losersPerAuction(),
                config.closingBatchSize(),
                baseline.durationMillis(),
                baseline.completedAuctions(),
                baseline.endToEndThroughputPerSecond(),
                withFailure.durationMillis(),
                withFailure.completedAuctions(),
                withFailure.pendingAuctions(),
                healthyCompletionRate,
                blockedHealthyAuctions,
                REPORT_PATH.toAbsolutePath()
        );
    }

    private record LoadConfig(
            int auctionCount,
            int losersPerAuction,
            long depositAmount,
            int closingBatchSize,
            long poisonAuctionId,
            int memberRows,
            int depositRows
    ) {

        private static LoadConfig fromEnvironment(int closingBatchSize) {
            int auctionCount = integerEnvironment("AUCTION_LOAD_COUNT", 100);
            int losersPerAuction = integerEnvironment(
                    "AUCTION_LOAD_LOSERS_PER_AUCTION", 20);
            long depositAmount = longEnvironment("AUCTION_LOAD_DEPOSIT_AMOUNT", 30_000L);
            long poisonAuctionId = longEnvironment("AUCTION_LOAD_POISON_AUCTION_ID", 1L);

            if (auctionCount < 2) {
                throw new IllegalArgumentException("AUCTION_LOAD_COUNT는 2 이상이어야 합니다.");
            }
            if (losersPerAuction < 1) {
                throw new IllegalArgumentException(
                        "AUCTION_LOAD_LOSERS_PER_AUCTION는 1 이상이어야 합니다.");
            }
            if (depositAmount < 2L || depositAmount > INITIAL_POINT) {
                throw new IllegalArgumentException(
                        "AUCTION_LOAD_DEPOSIT_AMOUNT는 2 이상 "
                                + INITIAL_POINT + " 이하여야 합니다.");
            }
            if (poisonAuctionId < 1L || poisonAuctionId > auctionCount) {
                throw new IllegalArgumentException(
                        "AUCTION_LOAD_POISON_AUCTION_ID는 생성할 경매 범위 안이어야 합니다.");
            }
            if (closingBatchSize <= 0) {
                throw new IllegalArgumentException("경매 마감 배치 크기는 양수여야 합니다.");
            }

            long depositRows = Math.multiplyExact(
                    (long) auctionCount,
                    losersPerAuction + 1L
            );
            if (depositRows > MAX_DEPOSIT_ROWS) {
                throw new IllegalArgumentException(
                        "보증금 fixture는 " + MAX_DEPOSIT_ROWS + "건을 넘을 수 없습니다.");
            }
            if (auctionCount > (long) closingBatchSize * 100L) {
                throw new IllegalArgumentException(
                        "경매 수는 한 번의 스케줄 실행 한도(batchSize * 100)를 넘을 수 없습니다.");
            }

            return new LoadConfig(
                    auctionCount,
                    losersPerAuction,
                    depositAmount,
                    closingBatchSize,
                    poisonAuctionId,
                    Math.toIntExact(depositRows + 1L),
                    Math.toIntExact(depositRows)
            );
        }

        private static int integerEnvironment(String name, int defaultValue) {
            return Math.toIntExact(longEnvironment(name, defaultValue));
        }

        private static long longEnvironment(String name, long defaultValue) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(name + "은 정수여야 합니다: " + value, exception);
            }
        }
    }

    private record ScenarioResult(
            String scenario,
            long durationMillis,
            long completedAuctions,
            long pendingAuctions,
            long trades,
            long refundedDeposits,
            long heldDeposits,
            String poisonAuctionStatus
    ) {

        private double endToEndThroughputPerSecond() {
            return completedAuctions * 1_000.0 / durationMillis;
        }

        private String toJson() {
            String poisonStatus = poisonAuctionStatus == null
                    ? "null"
                    : "\"" + poisonAuctionStatus + "\"";
            return String.format(Locale.ROOT, """
                    {
                        "scenario": "%s",
                        "durationMillis": %d,
                        "completedAuctions": %d,
                        "pendingAuctions": %d,
                        "trades": %d,
                        "refundedDeposits": %d,
                        "heldDeposits": %d,
                        "endToEndThroughputPerSecond": %.2f,
                        "poisonAuctionStatus": %s
                    }
                    """,
                    scenario,
                    durationMillis,
                    completedAuctions,
                    pendingAuctions,
                    trades,
                    refundedDeposits,
                    heldDeposits,
                    endToEndThroughputPerSecond(),
                    poisonStatus
            ).indent(2).stripTrailing();
        }
    }
}
