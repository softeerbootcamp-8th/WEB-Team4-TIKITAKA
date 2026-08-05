package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.Bid;
import com.tikitaka.bidwinback.auction.domain.entity.SealedBid;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.BidType;
import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.domain.exception.BidException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.BidRepository;
import com.tikitaka.bidwinback.auction.domain.repository.SealedBidRepository;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
import static com.tikitaka.bidwinback.global.exception.ErrorCode.BID_PHASE_CHANGED;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.BID_PRICE_TOO_LOW;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.CONCURRENT_BID_CONFLICT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_BID_UNIT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.NOT_UP_AUCTION;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.SELF_BID_NOT_ALLOWED;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.SEALED_BID_ALREADY_SUBMITTED;

@Service
@RequiredArgsConstructor
public class BidService {

    private static final long BID_UNIT = 1_000L;

    private final MemberRepository memberRepository;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final SealedBidRepository sealedBidRepository;

    @Transactional
    public BidResult place(
            Long memberId,
            Long auctionId,
            long price,
            BidType bidType
    ) {
        validateBidUnit(price);

        // bidType은 클라이언트가 인지한 입찰 단계일 뿐이며, 실제 단계는 DB 시각을 사용하는
        // 조건부 UPDATE가 판정한다. 단계가 바뀌어도 다른 입찰 유형으로 자동 전환하지 않는다.
        int updatedRows = switch (bidType) {
            case OPEN -> updateCurrentPrice(memberId, auctionId, price);
            case SEALED -> tryUpdateAuctionForSealedBid(memberId, auctionId, price);
        };
        if (updatedRows != 1) {
            // 실패 원인을 최신 상태로 다시 판별해 구체적인 도메인 오류로 변환한다.
            return rejectBid(memberId, auctionId, price, bidType);
        }

        return switch (bidType) {
            case OPEN -> saveOpenBid(memberId, auctionId, price);
            case SEALED -> saveSealedBid(memberId, auctionId, price);
        };
    }

    private BidResult saveOpenBid(Long memberId, Long auctionId, long price) {
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

    private BidResult saveSealedBid(Long memberId, Long auctionId, long price) {
        Auction auction = auctionRepository.getReferenceById(auctionId);
        Member bidder = memberRepository.getReferenceById(memberId);

        try {
            SealedBid sealedBid = sealedBidRepository.saveAndFlush(
                    SealedBid.builder()
                            .auction(auction)
                            .bidder(bidder)
                            .price(price)
                            .build()
            );
            return BidResult.from(sealedBid);
        } catch (DataIntegrityViolationException exception) {
            throw new BidException(SEALED_BID_ALREADY_SUBMITTED);
        }
    }

    private void validateBidUnit(long price) {
        if (price <= 0 || price % BID_UNIT != 0) {
            throw new BidException(INVALID_BID_UNIT);
        }
    }

    private int updateCurrentPrice(Long memberId, Long auctionId, long price) {
        try {
            return auctionRepository.updateCurrentPriceForBid(
                    auctionId,
                    memberId,
                    price,
                    BID_UNIT
            );
        } catch (PessimisticLockingFailureException | QueryTimeoutException exception) {
            throw new BidException(CONCURRENT_BID_CONFLICT);
        }
    }

    private int tryUpdateAuctionForSealedBid(Long memberId, Long auctionId, long price) {
        try {
            return auctionRepository.tryUpdateAuctionForSealedBid(
                    auctionId,
                    memberId,
                    price,
                    BID_UNIT
            );
        } catch (PessimisticLockingFailureException | QueryTimeoutException exception) {
            throw new BidException(CONCURRENT_BID_CONFLICT);
        }
    }

    private BidResult rejectBid(
            Long memberId,
            Long auctionId,
            long price,
            BidType bidType
    ) {
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
        if (currentBidType(auction, databaseTime) != bidType) {
            throw new BidException(BID_PHASE_CHANGED);
        }
        if (auction.getSeller().getId().equals(memberId)) {
            throw new BidException(SELF_BID_NOT_ALLOWED);
        }
        if (price - BID_UNIT < currentPriceOf(auction)) {
            throw new BidException(BID_PRICE_TOO_LOW);
        }

        throw new BidException(CONCURRENT_BID_CONFLICT);
    }

    private BidType currentBidType(Auction auction, LocalDateTime databaseTime) {
        LocalDateTime sealedBidStartedAt = auction.getEndedAt().minusMinutes(5);
        return databaseTime.isBefore(sealedBidStartedAt)
                ? BidType.OPEN
                : BidType.SEALED;
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
