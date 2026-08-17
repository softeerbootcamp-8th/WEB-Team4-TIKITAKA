UPDATE auction
JOIN (
    SELECT ranked.auction_id,
           ranked.bidder_id,
           ranked.price
    FROM (
        SELECT bid.auction_id,
               bid.bidder_id,
               bid.price,
               ROW_NUMBER() OVER (
                   PARTITION BY bid.auction_id
                   ORDER BY bid.price DESC, bid.created_at ASC, bid.id ASC
               ) AS pick
        FROM bid
        WHERE bid.status = 'UP'
    ) AS ranked
    WHERE ranked.pick = 1
) AS top_bid ON top_bid.auction_id = auction.id
SET auction.current_bidder_id = top_bid.bidder_id,
    auction.current_price = COALESCE(auction.current_price, top_bid.price);

UPDATE auction
JOIN (
    SELECT ranked.auction_id,
           ranked.bidder_id,
           ranked.price
    FROM (
        SELECT sealed_bid.auction_id,
               sealed_bid.bidder_id,
               sealed_bid.price,
               ROW_NUMBER() OVER (
                   PARTITION BY sealed_bid.auction_id
                   ORDER BY sealed_bid.price DESC, sealed_bid.submitted_at ASC,
                            sealed_bid.id ASC
               ) AS pick
        FROM sealed_bid
    ) AS ranked
    WHERE ranked.pick = 1
) AS top_sealed ON top_sealed.auction_id = auction.id
SET auction.sealed_top_bidder_id = top_sealed.bidder_id,
    auction.sealed_top_price = top_sealed.price;
