CREATE INDEX idx_auction_active_current_price_desc_id_desc
    ON auction (
        auction_type,
        completed_at,
        current_price DESC,
        id DESC,
        started_at,
        ended_at
    );
