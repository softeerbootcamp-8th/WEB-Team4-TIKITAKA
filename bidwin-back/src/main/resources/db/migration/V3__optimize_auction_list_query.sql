CREATE INDEX idx_auction_ended_at_id
    ON auction (ended_at, id);

CREATE INDEX idx_auction_created_at_id
    ON auction (created_at, id);

CREATE INDEX idx_bid_auction_id_created_at_price
    ON bid (auction_id, created_at, price);
