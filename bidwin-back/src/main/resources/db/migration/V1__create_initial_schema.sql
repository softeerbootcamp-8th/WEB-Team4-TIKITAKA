CREATE TABLE member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(17) NOT NULL,
    phone_number VARCHAR(11) NOT NULL,
    nickname VARCHAR(10) NOT NULL,
    email VARCHAR(320) NOT NULL,
    password VARCHAR(128) NOT NULL,
    total_point BIGINT NOT NULL,
    profile_object_key VARCHAR(100) NOT NULL,
    status ENUM('PENDING', 'ACTIVE', 'DORMANT', 'BANNED', 'WITHDRAWN_PENDING', 'WITHDRAWN') NOT NULL,
    locked_point BIGINT NOT NULL,
    auth_version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_modified_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_email UNIQUE (email),
    CONSTRAINT uk_member_nickname UNIQUE (nickname)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE auction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    seller_id BIGINT NOT NULL,
    auction_type VARCHAR(31) NOT NULL,
    title VARCHAR(30) NOT NULL,
    description TEXT NOT NULL,
    status ENUM('OPEN', 'BID_ONGOING', 'WINNER_DETERMINING', 'COMPLETED', 'UNSOLD') NOT NULL,
    category ENUM('HOUSEHOLD', 'FOOD', 'FURNITURE') NOT NULL,
    start_price BIGINT NOT NULL,
    current_price BIGINT NULL,
    ended_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    revision BIGINT NOT NULL,
    trade_type ENUM('DELIVERY', 'DIRECT') NOT NULL,
    contact VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_modified_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_auction_seller FOREIGN KEY (seller_id) REFERENCES member (id),
    INDEX idx_auction_status_ended_at (status, ended_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE up_auction (
    auction_id BIGINT NOT NULL,
    buy_now_price BIGINT NULL,
    PRIMARY KEY (auction_id),
    CONSTRAINT fk_up_auction_auction FOREIGN KEY (auction_id) REFERENCES auction (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE down_auction (
    auction_id BIGINT NOT NULL,
    minimum_price BIGINT NOT NULL,
    drop_price BIGINT NOT NULL,
    price_drop_interval BIGINT NOT NULL,
    PRIMARY KEY (auction_id),
    CONSTRAINT fk_down_auction_auction FOREIGN KEY (auction_id) REFERENCES auction (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE bid (
    id BIGINT NOT NULL AUTO_INCREMENT,
    auction_id BIGINT NOT NULL,
    bidder_id BIGINT NOT NULL,
    price BIGINT NOT NULL,
    status ENUM('UP', 'SEALED', 'DOWN', 'BUY_NOW') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_modified_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_bid_auction FOREIGN KEY (auction_id) REFERENCES auction (id),
    CONSTRAINT fk_bid_bidder FOREIGN KEY (bidder_id) REFERENCES member (id),
    INDEX idx_bid_auction_id_price (auction_id, price)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE sealed_bid (
    id BIGINT NOT NULL AUTO_INCREMENT,
    auction_id BIGINT NOT NULL,
    bidder_id BIGINT NOT NULL,
    price BIGINT NOT NULL,
    submitted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_sealed_bid_auction_bidder UNIQUE (auction_id, bidder_id),
    CONSTRAINT fk_sealed_bid_auction FOREIGN KEY (auction_id) REFERENCES auction (id),
    CONSTRAINT fk_sealed_bid_bidder FOREIGN KEY (bidder_id) REFERENCES member (id),
    INDEX idx_sealed_bid_auction_price (auction_id, price)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE auction_deposit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    auction_id BIGINT NOT NULL,
    reserved_amount BIGINT NOT NULL,
    status ENUM('HELD', 'REFUNDED', 'FORFEITED', 'USED') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_modified_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_auction_deposit_member_auction UNIQUE (member_id, auction_id),
    CONSTRAINT fk_auction_deposit_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_auction_deposit_auction FOREIGN KEY (auction_id) REFERENCES auction (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE auction_trade (
    id BIGINT NOT NULL AUTO_INCREMENT,
    auction_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    status ENUM('WAITING_CONFIRM', 'CONFIRMED', 'COMPLETED', 'BUYER_FAILED', 'SELLER_FAILED') NOT NULL,
    final_price BIGINT NOT NULL,
    purchased_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_modified_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_auction_trade_auction UNIQUE (auction_id),
    CONSTRAINT fk_auction_trade_auction FOREIGN KEY (auction_id) REFERENCES auction (id),
    CONSTRAINT fk_auction_trade_buyer FOREIGN KEY (buyer_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE instant_purchase_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    idempotency_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    buyer_id BIGINT NOT NULL,
    auction_id BIGINT NOT NULL,
    trade_id BIGINT NULL,
    final_price BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_instant_purchase_request_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_instant_purchase_request_trade UNIQUE (trade_id),
    CONSTRAINT fk_instant_purchase_request_trade FOREIGN KEY (trade_id) REFERENCES auction_trade (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE image (
    id BIGINT NOT NULL AUTO_INCREMENT,
    auction_id BIGINT NOT NULL,
    object_key VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_image_object_key UNIQUE (object_key),
    CONSTRAINT fk_image_auction FOREIGN KEY (auction_id) REFERENCES auction (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE email_verification_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version INT NOT NULL,
    member_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    last_modified_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_email_verification_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_verification_token_member FOREIGN KEY (member_id) REFERENCES member (id),
    INDEX idx_email_verification_token_member_id (member_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE password_reset_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version INT NOT NULL,
    member_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    last_modified_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_token_member FOREIGN KEY (member_id) REFERENCES member (id),
    INDEX idx_password_reset_token_member_id (member_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE pending_auction_image (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    draft_id BINARY(16) NOT NULL,
    object_key VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_modified_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_pending_auction_image_object_key UNIQUE (object_key),
    INDEX idx_pending_auction_image_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE pending_profile_image (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    object_key VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_modified_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_pending_profile_image_object_key UNIQUE (object_key),
    INDEX idx_pending_profile_image_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
