UPDATE auction AS a
INNER JOIN (
    SELECT auction_id, COUNT(*) AS sealed_bid_count
    FROM sealed_bid
    GROUP BY auction_id
) AS counts ON counts.auction_id = a.id
SET a.bid_count = a.bid_count + counts.sealed_bid_count
WHERE a.auction_type = 'UP'
  AND a.status IN ('WINNER_DETERMINING', 'COMPLETED', 'UNSOLD');
