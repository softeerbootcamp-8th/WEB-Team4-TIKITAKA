-- Run against an empty, isolated schema created for the auction-list load test.
-- Example:
--   mysql -h 127.0.0.1 -P 3307 -u root -p bidwin_perf_dev8 \
--     < load-tests/sql/auction-list-seed.sql

SET @seed_time = NOW(6);

INSERT INTO member (
    name,
    phone_number,
    nickname,
    email,
    password,
    total_point,
    profile_object_key,
    status,
    locked_point,
    auth_version,
    created_at,
    last_modified_at
)
SELECT
    'load test seller',
    '01000000000',
    'loadseller',
    'load-seller@bidwin.test',
    'unused',
    0,
    '',
    'ACTIVE',
    0,
    0,
    @seed_time,
    @seed_time
WHERE NOT EXISTS (
    SELECT 1
    FROM member
    WHERE email = 'load-seller@bidwin.test'
);

SET @seller_id = (
    SELECT id
    FROM member
    WHERE email = 'load-seller@bidwin.test'
);

CREATE TEMPORARY TABLE perf_numbers (
    n INT NOT NULL PRIMARY KEY
);

SET SESSION cte_max_recursion_depth = 100000;

INSERT INTO perf_numbers (n)
WITH RECURSIVE sequence (n) AS (
    SELECT 0
    UNION ALL
    SELECT n + 1
    FROM sequence
    WHERE n < 99999
)
SELECT n
FROM sequence;

INSERT INTO auction (
    seller_id,
    auction_type,
    title,
    description,
    status,
    category,
    start_price,
    current_price,
    bid_count,
    sealed_bid_count,
    ended_at,
    started_at,
    completed_at,
    revision,
    trade_type,
    contact,
    created_at,
    last_modified_at,
    current_bidder_id,
    sealed_top_price,
    sealed_top_bidder_id
)
SELECT
    @seller_id,
    IF(MOD(numbers.n, 2) = 0, 'UP', 'DOWN'),
    CONCAT('perf-auction-', LPAD(numbers.n, 5, '0')),
    'Auction-list performance test fixture',
    CASE MOD(FLOOR(numbers.n / 2), 3)
        WHEN 0 THEN 'BID_ONGOING'
        WHEN 1 THEN 'COMPLETED'
        ELSE 'BID_ONGOING'
    END,
    ELT(MOD(numbers.n, 9) + 1,
        'HOUSEHOLD',
        'FOOD',
        'FURNITURE',
        'ELECTRONICS',
        'FASHION',
        'SPORTS',
        'HOBBY',
        'BOOK',
        'OTHER'
    ),
    100000 + (MOD(numbers.n, 1000) * 1000),
    100000 + (MOD(numbers.n, 1000) * 1000) + (MOD(numbers.n, 20) * 1000),
    MOD(numbers.n, 50),
    0,
    CASE MOD(FLOOR(numbers.n / 2), 3)
        WHEN 0 THEN DATE_ADD(@seed_time, INTERVAL (30 + MOD(numbers.n, 7)) DAY)
        ELSE DATE_SUB(@seed_time, INTERVAL (1 + MOD(numbers.n, 7)) DAY)
    END,
    DATE_SUB(@seed_time, INTERVAL (8 + MOD(numbers.n, 7)) DAY),
    CASE MOD(FLOOR(numbers.n / 2), 3)
        WHEN 1 THEN DATE_SUB(@seed_time, INTERVAL (1 + MOD(numbers.n, 7)) DAY)
        ELSE NULL
    END,
    0,
    IF(MOD(numbers.n, 2) = 0, 'DELIVERY', 'DIRECT'),
    'load-test',
    DATE_SUB(@seed_time, INTERVAL MOD(numbers.n, 86400) SECOND),
    @seed_time,
    NULL,
    NULL,
    NULL
FROM perf_numbers numbers
WHERE NOT EXISTS (
    SELECT 1
    FROM auction
    WHERE title LIKE 'perf-auction-%'
);

INSERT INTO up_auction (auction_id, buy_now_price)
SELECT
    auction.id,
    auction.start_price * 2
FROM auction
WHERE auction.title LIKE 'perf-auction-%'
  AND auction.auction_type = 'UP'
  AND NOT EXISTS (
      SELECT 1
      FROM up_auction
      WHERE up_auction.auction_id = auction.id
  );

INSERT INTO down_auction (
    auction_id,
    minimum_price,
    drop_price,
    price_drop_interval
)
SELECT
    auction.id,
    GREATEST(1000, auction.start_price - 50000),
    1000,
    10
FROM auction
WHERE auction.title LIKE 'perf-auction-%'
  AND auction.auction_type = 'DOWN'
  AND NOT EXISTS (
      SELECT 1
      FROM down_auction
      WHERE down_auction.auction_id = auction.id
  );

SELECT
    @seed_time AS seeded_at,
    COUNT(*) AS auction_count,
    SUM(auction_type = 'UP') AS up_count,
    SUM(auction_type = 'DOWN') AS down_count,
    SUM(ended_at > @seed_time AND completed_at IS NULL) AS active_count,
    SUM(ended_at <= @seed_time AND completed_at IS NOT NULL) AS ended_completed_count,
    SUM(ended_at <= @seed_time AND completed_at IS NULL) AS ended_pending_count
FROM auction
WHERE title LIKE 'perf-auction-%';
