CREATE INDEX idx_auction_snapshot_latest
    ON auction (
        auction_type,
        completed_at,
        created_at DESC,
        id DESC,
        started_at,
        ended_at
    );

CREATE INDEX idx_auction_snapshot_deadline
    ON auction (
        auction_type,
        completed_at,
        ended_at,
        id,
        started_at
    );
