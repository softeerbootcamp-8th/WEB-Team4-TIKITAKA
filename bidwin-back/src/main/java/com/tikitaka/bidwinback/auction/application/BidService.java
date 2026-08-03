package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.Bid;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.domain.exception.BidException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.BidRepository;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_NOT_FOUND;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.BID_PRICE_TOO_LOW;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_BID_UNIT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.NOT_UP_AUCTION;

@Service
@RequiredArgsConstructor
public class BidService {

    private static final long BID_UNIT = 1_000L;

    private final MemberRepository memberRepository;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;

    @Transactional
    public BidResult place(Long memberId, Long auctionId, long price) {
        validateBidUnit(price);

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionException(AUCTION_NOT_FOUND));

        // 하향 경매는 즉시구매로만 거래되므로 입찰은 상향 경매에만 허용한다.
        if (!(auction instanceof UpAuction)) {
            throw new BidException(NOT_UP_AUCTION);
        }

        Long highestPrice = bidRepository.findHighestPriceByAuctionId(auctionId);
        long currentPrice = highestPrice == null ? auction.getStartPrice() : highestPrice;
        if (price - currentPrice < BID_UNIT) {
            throw new BidException(BID_PRICE_TOO_LOW);
        }

        // 인증 필터가 검증한 회원이므로 추가 조회 없이 FK 참조만 연결한다.
        Member bidder = memberRepository.getReferenceById(memberId);
        Bid bid = bidRepository.save(Bid.builder()
                .auction(auction)
                .bidder(bidder)
                .price(price)
                .status(BidStatus.UP)
                .build());

        return BidResult.from(bid);
    }

    private void validateBidUnit(long price) {
        if (price <= 0 || price % BID_UNIT != 0) {
            throw new BidException(INVALID_BID_UNIT);
        }
    }
}
