ALTER TABLE bid
    DROP INDEX idx_bid_auction_history,
    ADD INDEX idx_bid_auction_history
        (auction_id, created_at DESC, id DESC, bidder_id, price);

ALTER TABLE sealed_bid
    DROP INDEX idx_sealed_bid_auction_history,
    ADD INDEX idx_sealed_bid_auction_history
        (auction_id, submitted_at DESC, id DESC, bidder_id, price);
