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
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus.BID_ONGOING;
import static com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus.OPEN;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_ALREADY_ENDED;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_NOT_FOUND;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_NOT_ONGOING;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.BID_PRICE_TOO_LOW;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.CONCURRENT_BID_CONFLICT;
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

        if (updateCurrentPrice(auctionId, price) != 1) {
            // 실패 원인을 최신 상태로 다시 판별해 구체적인 도메인 오류로 변환한다.
            throwBidRejection(auctionId, price);
        }

        // 인증 필터가 검증한 회원은 추가 조회 없이 프록시 참조로 FK만 연결한다.
        Auction auction = auctionRepository.getReferenceById(auctionId);
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

    private int updateCurrentPrice(Long auctionId, long price) {
        try {
            return auctionRepository.updateCurrentPriceForBid(
                    auctionId,
                    price,
                    BID_UNIT
            );
        } catch (PessimisticLockingFailureException | QueryTimeoutException exception) {
            throw new BidException(CONCURRENT_BID_CONFLICT);
        }
    }

    private void throwBidRejection(Long auctionId, long price) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionException(AUCTION_NOT_FOUND));

        // 하향 경매는 즉시구매로만 거래되므로 입찰은 상향 경매에만 허용한다.
        if (!(auction instanceof UpAuction)) {
            throw new BidException(NOT_UP_AUCTION);
        }
        if (auction.getStatus() != OPEN && auction.getStatus() != BID_ONGOING) {
            throw new AuctionException(AUCTION_NOT_ONGOING);
        }

        LocalDateTime databaseTime = auctionRepository.currentDatabaseTime();
        if (!auction.getEndedAt().isAfter(databaseTime)) {
            throw new AuctionException(AUCTION_ALREADY_ENDED);
        }
        if (price < currentPriceOf(auction) + BID_UNIT) {
            throw new BidException(BID_PRICE_TOO_LOW);
        }

        throw new BidException(CONCURRENT_BID_CONFLICT);
    }

    private long currentPriceOf(Auction auction) {
        if (auction.hasCurrentPrice()) {
            return auction.getCurrentPrice();
        }

        // 스키마 변경 전에 생성된 경매만 Bid 최고가로 현재가를 보정한다.
        Long highestPrice = bidRepository.findHighestPriceByAuctionId(auction.getId());
        return highestPrice == null ? auction.getStartPrice() : highestPrice;
    }
}
