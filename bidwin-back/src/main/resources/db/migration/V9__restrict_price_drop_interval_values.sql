-- 인하 주기를 1/3/5/10분 중 하나로만 제한한다(PriceDropInterval enum과 일치).
--
-- 아직 완료되지 않은 하향 경매는 price_drop_interval이 실시간 현재가 계산
-- (DownAuctionCurrentPriceCalculator: elapsedDrops = 경과분 / priceDropInterval)에
-- 그대로 쓰인다. 주기 값만 바꾸면 지금까지 지난 하락 단계 수가 달라져 마이그레이션
-- 직후 현재가가 여러 단계 갑자기 떨어질 수 있다. 그래서 started_at도 함께 보정해
-- "지금까지 지난 하락 단계 수"는 유지하고, 그 이후부터만 새 주기로 하락하게 한다.
--
-- started_at 보정은 price_drop_interval이 아직 이전 값일 때(한 UPDATE 안에서 같은
-- 컬럼을 동시에 읽고 쓰면 엔진에 따라 이미 바뀐 새 값을 읽을 수 있어) 반드시 별도
-- UPDATE로 먼저 수행한 뒤, price_drop_interval을 보정한다.
UPDATE down_auction da
JOIN auction a ON a.id = da.auction_id
SET a.started_at = SYSDATE(6) - INTERVAL (
        FLOOR(
            TIMESTAMPDIFF(MINUTE, a.started_at, SYSDATE(6))
            / NULLIF(da.price_drop_interval, 0)
        )
        * (CASE
            WHEN da.price_drop_interval <= 2 THEN 1
            WHEN da.price_drop_interval <= 4 THEN 3
            WHEN da.price_drop_interval <= 7 THEN 5
            ELSE 10
        END)
    ) MINUTE
WHERE da.price_drop_interval NOT IN (1, 3, 5, 10)
  AND a.completed_at IS NULL;

-- 이미 완료된 경매는 실거래가가 확정되어 이 값이 더 이상 가격 계산에 쓰이지 않으므로
-- started_at을 건드릴 필요 없이 값만 허용 범위로 보정한다.
UPDATE down_auction da
JOIN auction a ON a.id = da.auction_id
SET da.price_drop_interval = CASE
        WHEN da.price_drop_interval <= 2 THEN 1
        WHEN da.price_drop_interval <= 4 THEN 3
        WHEN da.price_drop_interval <= 7 THEN 5
        ELSE 10
    END
WHERE da.price_drop_interval NOT IN (1, 3, 5, 10)
  AND a.completed_at IS NOT NULL;

-- started_at 보정이 끝난 뒤에야 price_drop_interval을 실제로 허용 범위로 바꾼다.
UPDATE down_auction
SET price_drop_interval = CASE
        WHEN price_drop_interval <= 2 THEN 1
        WHEN price_drop_interval <= 4 THEN 3
        WHEN price_drop_interval <= 7 THEN 5
        ELSE 10
    END
WHERE price_drop_interval NOT IN (1, 3, 5, 10);

ALTER TABLE down_auction
    ADD CONSTRAINT chk_down_auction_price_drop_interval
    CHECK (price_drop_interval IN (1, 3, 5, 10));
