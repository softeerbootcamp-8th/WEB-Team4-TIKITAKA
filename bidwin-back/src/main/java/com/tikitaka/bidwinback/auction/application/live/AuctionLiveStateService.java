package com.tikitaka.bidwinback.auction.application.live;

import com.tikitaka.bidwinback.auction.application.BuyNowPriceCalculator;
import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionTradeRepository;
import com.tikitaka.bidwinback.auction.domain.repository.BidRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class AuctionLiveStateService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final AuctionTradeRepository auctionTradeRepository;
    private final BuyNowPriceCalculator priceCalculator;

    /**
     * 경매·입찰·거래를 같은 DB read view에서 읽어 섞인 시점의 이벤트를 막는다.
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public AuctionLiveState getState(long auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionException(AUCTION_NOT_FOUND));
        LocalDateTime databaseTime = auctionRepository.currentDatabaseTime();
        return toState(auction, databaseTime);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<AuctionLiveState> getStates(Collection<Long> auctionIds) {
        List<Auction> auctions = auctionRepository.findAllById(auctionIds);
        LocalDateTime databaseTime = auctionRepository.currentDatabaseTime();
        return auctions.stream()
                .map(auction -> toState(auction, databaseTime))
                .toList();
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public long getDatabaseTimeMillis() {
        return auctionRepository.currentDatabaseTime()
                .atZone(SERVICE_ZONE)
                .toInstant()
                .toEpochMilli();
    }

    /**
     * 상세 응답과 같은 값을 내야 한다. 어긋나면 상세를 연 직후 첫 SSE 이벤트에서 숫자가 튄다.
     */
    private AuctionLiveState toState(
            Auction auction,
            LocalDateTime databaseTime
    ) {
        AuctionType auctionType = auctionTypeOf(auction);

        return new AuctionLiveState(
                auction.getId(),
                auction.getRevision(),
                auctionType,
                auction.getStatus(),
                currentPrice(auction, auctionType, databaseTime),
                bidRepository.countByAuctionId(auction.getId())
        );
    }

    private AuctionType auctionTypeOf(Auction auction) {
        if (auction instanceof UpAuction) {
            return AuctionType.UP;
        }
        if (auction instanceof DownAuction) {
            return AuctionType.DOWN;
        }
        throw new IllegalStateException("지원하지 않는 경매 유형입니다.");
    }

    private long currentPrice(
            Auction auction,
            AuctionType auctionType,
            LocalDateTime databaseTime
    ) {
        if (auction.getStatus() == AuctionStatus.COMPLETED) {
            return auctionTradeRepository.findFinalPriceByAuctionId(auction.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "완료된 경매의 확정 거래가가 없습니다. auctionId="
                                    + auction.getId()
                    ));
        }

        return switch (auctionType) {
            case UP -> auction.getCurrentPrice();
            case DOWN -> priceCalculator.calculate(
                    auction,
                    databaseTime.isAfter(auction.getEndedAt())
                            ? auction.getEndedAt()
                            : databaseTime
            );
        };
    }

}
