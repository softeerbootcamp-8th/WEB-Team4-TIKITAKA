EXPLAIN ANALYZE
SELECT
    candidate.auction_id,
    candidate.sort_at
FROM (
    (
        SELECT auction.id AS auction_id, auction.ended_at AS sort_at
        FROM auction AS auction USE INDEX (idx_auction_snapshot_deadline)
        WHERE auction.started_at <= NOW(6)
          AND auction.completed_at <= NOW(6)
          AND auction.auction_type = 'UP'
        ORDER BY auction.ended_at ASC, auction.id ASC
        LIMIT 16
    )
    UNION ALL
    (
        SELECT auction.id AS auction_id, auction.ended_at AS sort_at
        FROM auction AS auction USE INDEX (idx_auction_snapshot_deadline)
        WHERE auction.started_at <= NOW(6)
          AND auction.completed_at IS NULL
          AND auction.ended_at <= NOW(6)
          AND auction.auction_type = 'UP'
        ORDER BY auction.ended_at ASC, auction.id ASC
        LIMIT 16
    )
    UNION ALL
    (
        SELECT auction.id AS auction_id, auction.ended_at AS sort_at
        FROM auction AS auction USE INDEX (idx_auction_snapshot_deadline)
        WHERE auction.started_at <= NOW(6)
          AND auction.completed_at > NOW(6)
          AND auction.ended_at <= NOW(6)
          AND auction.auction_type = 'UP'
        ORDER BY auction.ended_at ASC, auction.id ASC
        LIMIT 16
    )
    UNION ALL
    (
        SELECT auction.id AS auction_id, auction.ended_at AS sort_at
        FROM auction AS auction USE INDEX (idx_auction_snapshot_deadline)
        WHERE auction.started_at <= NOW(6)
          AND auction.completed_at <= NOW(6)
          AND auction.auction_type = 'DOWN'
        ORDER BY auction.ended_at ASC, auction.id ASC
        LIMIT 16
    )
    UNION ALL
    (
        SELECT auction.id AS auction_id, auction.ended_at AS sort_at
        FROM auction AS auction USE INDEX (idx_auction_snapshot_deadline)
        WHERE auction.started_at <= NOW(6)
          AND auction.completed_at IS NULL
          AND auction.ended_at <= NOW(6)
          AND auction.auction_type = 'DOWN'
        ORDER BY auction.ended_at ASC, auction.id ASC
        LIMIT 16
    )
    UNION ALL
    (
        SELECT auction.id AS auction_id, auction.ended_at AS sort_at
        FROM auction AS auction USE INDEX (idx_auction_snapshot_deadline)
        WHERE auction.started_at <= NOW(6)
          AND auction.completed_at > NOW(6)
          AND auction.ended_at <= NOW(6)
          AND auction.auction_type = 'DOWN'
        ORDER BY auction.ended_at ASC, auction.id ASC
        LIMIT 16
    )
) AS candidate
ORDER BY candidate.sort_at ASC, candidate.auction_id ASC
LIMIT 16 OFFSET 0;
