-- 인하 주기를 1/3/5/10분 중 하나로만 제한한다(PriceDropInterval enum과 일치).
-- 기존에 저장된 값 중 허용 범위를 벗어난 값은 가장 가까운 허용 값으로 보정한 뒤 제약을 건다.
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
