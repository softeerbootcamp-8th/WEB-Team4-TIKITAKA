ALTER TABLE auction
    ADD COLUMN bid_count BIGINT NOT NULL DEFAULT 0 AFTER current_price;

UPDATE auction AS a
LEFT JOIN (
    SELECT auction_id, COUNT(*) AS bid_count
    FROM bid
    GROUP BY auction_id
) AS counts ON counts.auction_id = a.id
SET a.bid_count = COALESCE(counts.bid_count, 0);

CREATE INDEX idx_auction_recommended
    ON auction (
        auction_type,
        completed_at,
        bid_count DESC,
        id DESC,
        started_at,
        ended_at
    );
