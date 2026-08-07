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
import com.tikitaka.bidwinback.auction.domain.repository.SealedBidRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionBidSummary;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionFinalPrice;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionSealedBidCount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class AuctionLiveStateService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final SealedBidRepository sealedBidRepository;
    private final AuctionTradeRepository auctionTradeRepository;
    private final BuyNowPriceCalculator priceCalculator;

    /**
     * 경매·입찰·거래를 같은 DB read view에서 읽어 섞인 시점의 이벤트를 막는다.
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public AuctionLiveState getState(long auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionException(AUCTION_NOT_FOUND));
        return toStates(List.of(auction)).get(0);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<AuctionLiveState> getStates(Collection<Long> auctionIds) {
        List<Auction> auctions = auctionRepository.findAllById(auctionIds);
        return toStates(auctions);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public long getDatabaseTimeMillis() {
        return auctionRepository.currentDatabaseTime()
                .atZone(SERVICE_ZONE)
                .toInstant()
                .toEpochMilli();
    }

    /**
     * 목록 구독은 경매 수만큼 개별 집계를 돌리면 재연결마다 쿼리가 폭증하므로,
     * 입찰 집계·밀봉 입찰 수·확정 거래가를 IN 절로 한 번씩만 모아 상태를 조립한다.
     */
    private List<AuctionLiveState> toStates(List<Auction> auctions) {
        if (auctions.isEmpty()) {
            return List.of();
        }

        LocalDateTime databaseTime = auctionRepository.currentDatabaseTime();
        List<Long> auctionIds = auctions.stream().map(Auction::getId).toList();

        Map<Long, AuctionBidSummary> bidSummaries = bidRepository.summarizeAllByAuctionIds(auctionIds)
                .stream()
                .collect(Collectors.toMap(AuctionBidSummary::auctionId, Function.identity()));
        Map<Long, Long> sealedBidCounts = sealedBidRepository.countByAuctionIds(auctionIds)
                .stream()
                .collect(Collectors.toMap(
                        AuctionSealedBidCount::auctionId,
                        AuctionSealedBidCount::bidCount
                ));
        Map<Long, Long> finalPrices = auctionTradeRepository.findFinalPricesByAuctionIds(auctionIds)
                .stream()
                .collect(Collectors.toMap(
                        AuctionFinalPrice::auctionId,
                        AuctionFinalPrice::finalPrice
                ));

        return auctions.stream()
                .map(auction -> toState(
                        auction,
                        databaseTime,
                        bidSummaries,
                        sealedBidCounts,
                        finalPrices
                ))
                .toList();
    }

    /**
     * 상세 응답과 같은 값을 내야 한다. 어긋나면 상세를 연 직후 첫 SSE 이벤트에서 숫자가 튄다.
     */
    private AuctionLiveState toState(
            Auction auction,
            LocalDateTime databaseTime,
            Map<Long, AuctionBidSummary> bidSummaries,
            Map<Long, Long> sealedBidCounts,
            Map<Long, Long> finalPrices
    ) {
        AuctionType auctionType = auctionTypeOf(auction);
        AuctionBidSummary bidSummary = bidSummaries.get(auction.getId());

        return new AuctionLiveState(
                auction.getId(),
                auction.getRevision(),
                auctionType,
                auction.getStatus(),
                currentPrice(auction, auctionType, databaseTime, bidSummary, finalPrices),
                bidCount(auction, auctionType, bidSummary, sealedBidCounts)
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

    private long bidCount(
            Auction auction,
            AuctionType auctionType,
            AuctionBidSummary bidSummary,
            Map<Long, Long> sealedBidCounts
    ) {
        long count = bidSummary == null ? 0L : bidSummary.bidCount();
        // 상세 조회와 동일하게, 밀봉이 공개된 상향 경매만 밀봉 입찰 수를 합산한다.
        if (auctionType == AuctionType.UP && auction.isSealedBidRevealed()) {
            count += sealedBidCounts.getOrDefault(auction.getId(), 0L);
        }
        return count;
    }

    private long currentPrice(
            Auction auction,
            AuctionType auctionType,
            LocalDateTime databaseTime,
            AuctionBidSummary bidSummary,
            Map<Long, Long> finalPrices
    ) {
        if (auction.getStatus() == AuctionStatus.COMPLETED) {
            Long finalPrice = finalPrices.get(auction.getId());
            if (finalPrice == null) {
                throw new IllegalStateException(
                        "완료된 경매의 확정 거래가가 없습니다. auctionId=" + auction.getId()
                );
            }
            return finalPrice;
        }

        return switch (auctionType) {
            case UP -> upCurrentPrice(auction, bidSummary);
            case DOWN -> priceCalculator.calculate(
                    auction,
                    databaseTime.isAfter(auction.getEndedAt())
                            ? auction.getEndedAt()
                            : databaseTime
            );
        };
    }

    private long upCurrentPrice(Auction auction, AuctionBidSummary bidSummary) {
        if (auction.hasCurrentPrice()) {
            return auction.getCurrentPrice();
        }

        // 스키마 변경 전 생성돼 current_price가 null인 경매만 Bid 최고가로 보정한다(상세 조회와 동일).
        Long highestPrice = bidSummary == null ? null : bidSummary.highestPrice();
        return highestPrice == null ? auction.getStartPrice() : highestPrice;
    }

}
