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
import static com.tikitaka.bidwinback.global.exception.ErrorCode.NOT_UP_AUCTION;

@Service
@RequiredArgsConstructor
public class BidService {

    private final MemberRepository memberRepository;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;

    /**
     * 입찰 한 건을 Bid 테이블에 기록한다.
     * 경매 상태·호가 단위·보증금·자기 경매 입찰 같은 정합성 검증은 후속 작업에서 붙인다.
     */
    @Transactional
    public BidResult place(Long memberId, Long auctionId, long price) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionException(AUCTION_NOT_FOUND));

        // 하향 경매는 즉시구매로만 거래되므로 입찰은 상향 경매에만 허용한다.
        if (!(auction instanceof UpAuction)) {
            throw new BidException(NOT_UP_AUCTION);
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
}
