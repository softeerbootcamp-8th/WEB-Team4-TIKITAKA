-- 스키마 변경 전에 생성된 상향 경매의 NULL 현재가를 한 번 보정한다.
UPDATE auction AS a
SET a.current_price = COALESCE(
        (SELECT MAX(b.price) FROM bid AS b WHERE b.auction_id = a.id),
        a.start_price
    )
WHERE a.auction_type = 'UP'
  AND a.current_price IS NULL;

-- 하향 낮은 가격순은 시간에 따라 변하지 않는 최저가를 하한으로 keyset scan한다.
CREATE INDEX idx_down_auction_minimum_price_id
    ON down_auction (minimum_price ASC, auction_id DESC);

-- 하향 높은 가격순 Top-K는 현재가가 넘을 수 없는 시작가부터 탐색한다.
CREATE INDEX idx_auction_start_price_id
    ON auction (auction_type, start_price DESC, id DESC);

-- 상향 경매는 저장된 current_price가 현재가 자체이므로 Top-K 계산 없이 바로 정렬한다.
CREATE INDEX idx_auction_current_price_asc_id_desc
    ON auction (auction_type, current_price ASC, id DESC);

CREATE INDEX idx_auction_current_price_desc_id_desc
    ON auction (auction_type, current_price DESC, id DESC);
