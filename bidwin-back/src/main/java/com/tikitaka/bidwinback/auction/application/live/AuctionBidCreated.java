package com.tikitaka.bidwinback.auction.application.live;

import com.tikitaka.bidwinback.auction.domain.entity.Bid;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidHistoryItemResponse;

/** 커밋 후 공개할 입찰 snapshot. 밀봉입찰은 마감 전에는 발행하지 않는다. */
public record AuctionBidCreated(
        long auctionId,
        long bidId,
        BidHistoryItemResponse bid
) {

    public static AuctionBidCreated from(Bid bid) {
        return new AuctionBidCreated(
                bid.getAuction().getId(),
                bid.getId(),
                BidHistoryItemResponse.of(
                        "BID:" + bid.getId(),
                        bid.getBidder().getNickname(),
                        bid.getPrice(),
                        bid.getCreatedAt()
                )
        );
    }
}
