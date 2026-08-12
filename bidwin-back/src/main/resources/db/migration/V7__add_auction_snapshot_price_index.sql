CREATE INDEX idx_auction_snapshot_price
    ON auction (
        auction_type,
        completed_at,
        start_price DESC,
        id DESC,
        started_at,
        ended_at
    );
