CREATE INDEX idx_auction_count
    ON auction (completed_at, auction_type, started_at, ended_at);
