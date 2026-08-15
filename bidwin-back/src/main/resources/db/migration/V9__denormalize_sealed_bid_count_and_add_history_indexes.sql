ALTER TABLE auction
    ADD COLUMN sealed_bid_count BIGINT NOT NULL DEFAULT 0 AFTER bid_count;

UPDATE auction AS a
LEFT JOIN (
    SELECT auction_id, COUNT(*) AS sealed_bid_count
    FROM sealed_bid
    GROUP BY auction_id
) AS counts ON counts.auction_id = a.id
SET a.sealed_bid_count = COALESCE(counts.sealed_bid_count, 0);

CREATE INDEX idx_bid_auction_history
    ON bid (auction_id, created_at DESC, id DESC);

CREATE INDEX idx_sealed_bid_auction_history
    ON sealed_bid (auction_id, submitted_at DESC, id DESC);
