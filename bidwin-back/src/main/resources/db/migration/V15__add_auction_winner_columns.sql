ALTER TABLE auction
    ADD COLUMN current_bidder_id BIGINT NULL,
    ADD COLUMN sealed_top_price BIGINT NULL,
    ADD COLUMN sealed_top_bidder_id BIGINT NULL;
