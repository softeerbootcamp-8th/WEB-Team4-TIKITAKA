package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.application.live.AuctionBidCreated;
import com.tikitaka.bidwinback.auction.application.live.AuctionStateChanged;
import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.AuctionDeposit;
import com.tikitaka.bidwinback.auction.domain.entity.Bid;
import com.tikitaka.bidwinback.auction.domain.entity.SealedBid;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.BidType;
import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.domain.exception.BidException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionDepositRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.BidRepository;
import com.tikitaka.bidwinback.auction.domain.repository.SealedBidRepository;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.context.ApplicationEventPublisher;
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
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INSUFFICIENT_DEPOSIT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.NOT_UP_AUCTION;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.SELF_BID_NOT_ALLOWED;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.SEALED_BID_ALREADY_SUBMITTED;

@Service
@RequiredArgsConstructor
public class BidService {

    private static final long BID_UNIT = 1_000L;
    private static final long SEALED_BID_WINDOW_MINUTES = 5L;
    private static final long DEPOSIT_RATE_NUMERATOR = 3L;
    private static final long DEPOSIT_RATE_DENOMINATOR = 10L;

    private final MemberRepository memberRepository;
    private final AuctionRepository auctionRepository;
    private final AuctionDepositRepository auctionDepositRepository;
    private final BidRepository bidRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SealedBidRepository sealedBidRepository;
    private final BidPriceCache bidPriceCache;
  
    @Transactional
    public BidResult place(
            Long memberId,
            Long auctionId,
            long price,
            BidType bidType
    ) {
        validateBidUnit(price);

        // Redis에서 즉시 원자적으로 승패를 가른다(비교+갱신을 한 번에). 이겼을 때 받은 "이전 값"은
        // MySQL에서 다른 이유로 최종 실패할 경우 되돌리는 데 쓴다. SEALED는 이 캐시 대상이 아니다.
        Long previousPrice = bidType == BidType.OPEN
                ? bidPriceCache.tryWinRace(auctionId, price)
                : null;
        if (bidPriceCache.isLost(previousPrice)) {
            throw new BidException(BID_PRICE_TOO_LOW);
        }

        // bidType은 클라이언트가 인지한 입찰 단계일 뿐이며, 실제 단계는 DB 시각을 사용하는
        // 조건부 UPDATE가 판정한다. 단계가 바뀌어도 다른 입찰 유형으로 자동 전환하지 않는다.
        int updatedRows = switch (bidType) {
            case OPEN -> updateCurrentPrice(memberId, auctionId, price);
            case SEALED -> tryUpdateAuctionForSealedBid(memberId, auctionId, price);
        };
        if (updatedRows != 1) {
            // Redis에서는 이겼다고 판정했지만 MySQL에서 다른 이유(본인 경매, 종료 등)로 최종 실패한
            // 경우, 그 사이 아무도 안 건드렸다면 캐시를 이전 값으로 되돌린다.
            if (previousPrice != null && !bidPriceCache.isLost(previousPrice)) {
                bidPriceCache.revertIfStillMine(auctionId, price, previousPrice);
            }
            // 실패 원인을 최신 상태로 다시 판별해 구체적인 도메인 오류로 변환한다.
            return rejectBid(memberId, auctionId, price, bidType);
        }

        Auction auction = auctionRepository.getReferenceById(auctionId);
        Member bidder = memberRepository.getReferenceById(memberId);
        reserveDepositForFirstBid(memberId, auctionId, auction, bidder);

        return switch (bidType) {
            case OPEN -> saveOpenBid(auction, bidder, price);
            case SEALED -> saveSealedBid(auction, bidder, price);
        };
    }

    private void reserveDepositForFirstBid(
            Long memberId,
            Long auctionId,
            Auction auction,
            Member bidder
    ) {
        if (auctionDepositRepository.existsByMemberIdAndAuctionId(memberId, auctionId)) {
            return;
        }

        // 시작가는 천원 단위이므로 나눗셈 후 곱해도 30%가 정확하며 곱셈 오버플로도 피한다.
        long depositAmount = Math.multiplyExact(
                auction.getStartPrice() / DEPOSIT_RATE_DENOMINATOR,
                DEPOSIT_RATE_NUMERATOR
        );
        int lockedPoints = memberRepository.movePointToLockedIfEnough(
                memberId,
                depositAmount
        );
        if (lockedPoints != 1) {
            throw new BidException(INSUFFICIENT_DEPOSIT);
        }

        auctionDepositRepository.save(AuctionDeposit.builder()
                .member(bidder)
                .auction(auction)
                .reservedAmount(depositAmount)
                .build());
    }

    private BidResult saveOpenBid(Auction auction, Member bidder, long price) {
        Bid bid = bidRepository.save(Bid.builder()
                .auction(auction)
                .bidder(bidder)
                .price(price)
                .status(BidStatus.UP)
                .build());

        eventPublisher.publishEvent(new AuctionStateChanged(auction.getId()));
        eventPublisher.publishEvent(new AuctionBidCreated(auction.getId(), bid.getId()));
        return BidResult.from(bid);
    }

    private BidResult saveSealedBid(Auction auction, Member bidder, long price) {
        SealedBid sealedBid;
        try {
            sealedBid = sealedBidRepository.saveAndFlush(
                    SealedBid.builder()
                            .auction(auction)
                            .bidder(bidder)
                            .price(price)
                            .build()
            );
        } catch (DataIntegrityViolationException exception) {
            throw new BidException(SEALED_BID_ALREADY_SUBMITTED);
        }

        // 최초 밀봉입찰의 OPEN -> BID_ONGOING 전환만 revision을 올린다.
        // 이후 이벤트는 같은 revision이라 연결에서 제거되어 비공개 입찰 횟수를 드러내지 않는다.
        eventPublisher.publishEvent(new AuctionStateChanged(auction.getId()));
        return BidResult.from(sealedBid);
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
        if (price - BID_UNIT < highestAcceptedPriceOf(auction, bidType)) {
            throw new BidException(BID_PRICE_TOO_LOW);
        }

        throw new BidException(CONCURRENT_BID_CONFLICT);
    }

    private BidType currentBidType(Auction auction, LocalDateTime databaseTime) {
        LocalDateTime sealedBidStartedAt = auction.getEndedAt().minusMinutes(
                SEALED_BID_WINDOW_MINUTES
        );
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

    private long highestAcceptedPriceOf(Auction auction, BidType bidType) {
        long currentPrice = currentPriceOf(auction);
        if (bidType != BidType.SEALED) {
            return currentPrice;
        }

        Long highestSealedPrice = sealedBidRepository.findHighestPriceByAuctionId(
                auction.getId()
        );
        return highestSealedPrice == null
                ? currentPrice
                : Math.max(currentPrice, highestSealedPrice);
    }
}
