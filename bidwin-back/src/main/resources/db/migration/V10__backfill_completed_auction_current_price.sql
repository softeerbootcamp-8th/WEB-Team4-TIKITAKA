UPDATE auction AS a
INNER JOIN auction_trade AS trade ON trade.auction_id = a.id
SET a.current_price = trade.final_price
WHERE a.status = 'COMPLETED'
  AND NOT (a.current_price <=> trade.final_price);
