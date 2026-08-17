package com.tikitaka.bidwinback.auction.application.live;

import com.tikitaka.bidwinback.auction.application.BuyNowPriceCalculator;
import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionTradeRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionFinalPrice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class AuctionLiveStateService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final AuctionRepository auctionRepository;
    private final AuctionTradeRepository auctionTradeRepository;
    private final BuyNowPriceCalculator priceCalculator;

    /**
     * 경매와 확정 거래를 같은 DB read view에서 읽어 섞인 시점의 이벤트를 막는다.
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
        // findAllById는 없는 ID를 조용히 누락하므로, 존재하지 않는 경매를 구독하면
        // 초기 snapshot 없이 채널만 붙는다. 단건 조회와 동일하게 명시적으로 거부한다.
        Set<Long> foundIds = auctions.stream()
                .map(Auction::getId)
                .collect(Collectors.toSet());
        if (auctionIds.stream().anyMatch(id -> !foundIds.contains(id))) {
            throw new AuctionException(AUCTION_NOT_FOUND);
        }
        return toStates(auctions);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public long getDatabaseTimeMillis() {
        return auctionRepository.currentDatabaseTime()
                .atZone(SERVICE_ZONE)
                .toInstant()
                .toEpochMilli();
    }

    /** 현재가와 입찰 수는 경매 행의 반정규화 값을 쓰고, 필요한 보조 값만 모은다. */
    private List<AuctionLiveState> toStates(List<Auction> auctions) {
        if (auctions.isEmpty()) {
            return List.of();
        }

        Optional<LocalDateTime> databaseTime = auctions.stream()
                .anyMatch(auction -> AuctionType.from(auction) == AuctionType.DOWN)
                ? Optional.of(auctionRepository.currentDatabaseTime())
                : Optional.empty();
        List<Long> completedAuctionIds = auctions.stream()
                .filter(auction -> auction.getStatus() == AuctionStatus.COMPLETED)
                .map(Auction::getId)
                .toList();

        Map<Long, Long> finalPrices = completedAuctionIds.isEmpty()
                ? Map.of()
                : auctionTradeRepository.findFinalPricesByAuctionIds(completedAuctionIds)
                        .stream()
                        .collect(Collectors.toMap(
                                AuctionFinalPrice::auctionId,
                                AuctionFinalPrice::finalPrice
                        ));

        return auctions.stream()
                .map(auction -> toState(
                        auction,
                        databaseTime,
                        finalPrices
                ))
                .toList();
    }

    /**
     * 상세 응답과 같은 값을 내야 한다. 어긋나면 상세를 연 직후 첫 SSE 이벤트에서 숫자가 튄다.
     */
    private AuctionLiveState toState(
            Auction auction,
            Optional<LocalDateTime> databaseTime,
            Map<Long, Long> finalPrices
    ) {
        AuctionType auctionType = AuctionType.from(auction);

        return new AuctionLiveState(
                auction.getId(),
                auction.getRevision(),
                auctionType,
                auction.getStatus(),
                currentPrice(auction, auctionType, databaseTime, finalPrices),
                bidCount(auction, auctionType)
        );
    }

    private long bidCount(Auction auction, AuctionType auctionType) {
        long count = auction.getBidCount();
        // 상세 조회와 동일하게, 밀봉이 공개된 상향 경매만 밀봉 입찰 수를 합산한다.
        if (auctionType == AuctionType.UP && auction.isSealedBidRevealed()) {
            count += auction.getSealedBidCount();
        }
        return count;
    }

    private long currentPrice(
            Auction auction,
            AuctionType auctionType,
            Optional<LocalDateTime> databaseTime,
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
            case UP -> auction.getCurrentPrice();
            case DOWN -> {
                LocalDateTime downPriceTime = databaseTime.orElseThrow();
                yield priceCalculator.calculate(
                        auction,
                        downPriceTime.isAfter(auction.getEndedAt())
                                ? auction.getEndedAt()
                                : downPriceTime
                );
            }
        };
    }

}
