CREATE TABLE down_price_snapshot (
    snapshot_at DATETIME(6) NOT NULL,
    auction_id  BIGINT      NOT NULL,
    price       BIGINT      NOT NULL,
    PRIMARY KEY (snapshot_at, auction_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX idx_down_price_snapshot_price_asc
    ON down_price_snapshot (snapshot_at, price ASC, auction_id DESC);

CREATE INDEX idx_down_price_snapshot_price_desc
    ON down_price_snapshot (snapshot_at, price DESC, auction_id DESC);
