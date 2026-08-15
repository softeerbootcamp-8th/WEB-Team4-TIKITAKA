CREATE INDEX idx_bid_winner
    ON bid (
        auction_id,
        status,
        price DESC,
        created_at ASC,
        id ASC,
        bidder_id,
        last_modified_at
    );

CREATE INDEX idx_sealed_bid_winner
    ON sealed_bid (
        auction_id,
        price DESC,
        submitted_at ASC,
        id ASC,
        bidder_id
    );
