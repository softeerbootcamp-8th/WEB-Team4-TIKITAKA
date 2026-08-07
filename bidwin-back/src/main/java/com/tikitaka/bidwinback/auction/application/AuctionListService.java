package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.BidRepository;
import com.tikitaka.bidwinback.auction.domain.repository.ImageRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionBidSummary;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionListResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionDownPricingResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionSummaryResponse;
import com.tikitaka.bidwinback.global.storage.ImageUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/*
 * 경매 목록 조회 — 1차 뼈대.
 *
 * 정렬·타입 필터·현재가 계산을 전부 DB 쿼리 하나로 밀어넣는 대신, 키워드로만 좁힌
 * 전체 목록을 애플리케이션으로 가져와 자바에서 계산·정렬·페이지를 자른다.
 * 데모 규모에서는 이게 훨씬 단순하고 검증하기 쉽고, 목록이 커져서 느려지면
 * 그때 계산을 DB 쿼리 쪽으로 옮기는 성능 개선을 하면 된다.
 */
@Service
@RequiredArgsConstructor
public class AuctionListService {

    private static final int FIRST_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 16;
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ImageRepository imageRepository;
    private final ImageUrlResolver imageUrlResolver;
    private final BuyNowPriceCalculator buyNowPriceCalculator;

    @Transactional(readOnly = true)
    public AuctionListResponse getList(AuctionListQuery query) {
        LocalDateTime serverTime = auctionRepository.currentDatabaseTime();
        LocalDateTime asOf = query.asOf() != null ? query.asOf() : serverTime;

        List<Auction> auctions = auctionRepository.findAllForList(query.keyword(), asOf)
                .stream()
                .filter(auction -> matchesType(auction, query.auctionType()))
                .toList();

        Map<Long, AuctionBidSummary> bidSummaries = fetchBidSummaries(auctions, asOf);
        Map<Long, String> thumbnails = fetchThumbnails(auctions);

        List<AuctionSummaryResponse> summaries = auctions.stream()
                .map(auction -> toSummary(auction, asOf, bidSummaries, thumbnails))
                .sorted(comparatorFor(query.sort()))
                .toList();

        int size = query.size() > 0 ? query.size() : DEFAULT_PAGE_SIZE;
        int totalPages = Math.max(FIRST_PAGE, (int) Math.ceil((double) summaries.size() / size));
        int currentPage = Math.min(Math.max(FIRST_PAGE, query.page()), totalPages);

        List<AuctionSummaryResponse> pageItems = slice(summaries, currentPage, size);

        return new AuctionListResponse(
                pageItems,
                toEpochMilli(serverTime),
                toEpochMilli(asOf),
                currentPage,
                totalPages,
                summaries.size()
        );
    }

    private boolean matchesType(Auction auction, AuctionType filter) {
        if (filter == null) {
            return true;
        }
        return filter == AuctionType.UP ? auction instanceof UpAuction : auction instanceof DownAuction;
    }

    private Map<Long, AuctionBidSummary> fetchBidSummaries(List<Auction> auctions, LocalDateTime asOf) {
        List<Long> auctionIds = auctions.stream().map(Auction::getId).toList();
        if (auctionIds.isEmpty()) {
            return Map.of();
        }
        return bidRepository.summarizeByAuctionIds(auctionIds, asOf)
                .stream()
                .collect(Collectors.toMap(AuctionBidSummary::auctionId, summary -> summary));
    }

    private Map<Long, String> fetchThumbnails(List<Auction> auctions) {
        List<Long> auctionIds = auctions.stream().map(Auction::getId).toList();
        if (auctionIds.isEmpty()) {
            return Map.of();
        }

        // findFirstImageByAuctionIds가 auctionId당 대표 이미지 한 장만 주므로,
        // 자바에서 따로 골라낼 필요 없이 그대로 Map으로 옮긴다.
        return imageRepository.findFirstImageByAuctionIds(auctionIds).stream()
                .collect(Collectors.toMap(
                        image -> image.getAuction().getId(),
                        image -> imageUrlResolver.resolve(image.getObjectKey())));
    }

    private AuctionSummaryResponse toSummary(
            Auction auction,
            LocalDateTime asOf,
            Map<Long, AuctionBidSummary> bidSummaries,
            Map<Long, String> thumbnails
    ) {
        AuctionBidSummary bidSummary = bidSummaries.get(auction.getId());
        long bidCount = bidSummary != null ? bidSummary.bidCount() : 0L;

        return new AuctionSummaryResponse(
                auction.getId(),
                auction instanceof UpAuction ? AuctionType.UP : AuctionType.DOWN,
                auction.getTitle(),
                auction.getSeller().getNickname(),
                auction.getCategory(),
                thumbnails.get(auction.getId()),
                computeCurrentPrice(auction, asOf, bidSummary),
                auction.getStartPrice(),
                bidCount,
                toEpochMilli(auction.getEndedAt()),
                toEpochMilli(auction.getCreatedAt()),
                auction.getStatus(),
                auction.getRevision(),
                downPricing(auction)
        );
    }

    private AuctionDownPricingResponse downPricing(Auction auction) {
        if (!(auction instanceof DownAuction downAuction)) {
            return null;
        }
        return new AuctionDownPricingResponse(
                downAuction.getMinimumPrice(),
                downAuction.getDropPrice(),
                Duration.ofMinutes(downAuction.getPriceDropInterval()).toMillis(),
                toEpochMilli(downAuction.getStartedAt())
        );
    }

    private long computeCurrentPrice(Auction auction, LocalDateTime asOf, AuctionBidSummary bidSummary) {
        if (auction instanceof UpAuction upAuction) {
            Long highestPrice = bidSummary != null ? bidSummary.highestPrice() : null;
            return highestPrice != null ? highestPrice : upAuction.getStartPrice();
        }
        if (auction instanceof DownAuction downAuction) {
            // 구매 확정가와 같은 공식·같은 기준 시각(startedAt)을 쓰도록 BuyNowPriceCalculator를 그대로
            // 재사용한다. 직접 계산했을 땐 createdAt(앱 서버 시각)을 썼는데, 구매 확정 로직은
            // startedAt(DB 시각)을 쓰고 있어 하락 주기 경계에서 목록가와 구매가가 어긋날 수 있었다.
            return buyNowPriceCalculator.calculate(downAuction, asOf);
        }
        throw new IllegalStateException("지원하지 않는 경매 유형입니다: " + auction.getClass());
    }

    private Comparator<AuctionSummaryResponse> comparatorFor(AuctionSort sort) {
        return switch (sort) {
            case RECOMMENDED -> Comparator.comparingLong(AuctionSummaryResponse::bidCount).reversed();
            case DEADLINE -> Comparator.comparingLong(AuctionSummaryResponse::deadline);
            case LATEST -> Comparator.comparingLong(AuctionSummaryResponse::listedAt).reversed();
            case PRICE_LOW -> Comparator.comparingLong(AuctionSummaryResponse::currentPrice);
            case PRICE_HIGH -> Comparator.comparingLong(AuctionSummaryResponse::currentPrice).reversed();
        };
    }

    private List<AuctionSummaryResponse> slice(List<AuctionSummaryResponse> items, int page, int size) {
        int fromIndex = Math.min((page - FIRST_PAGE) * size, items.size());
        int toIndex = Math.min(fromIndex + size, items.size());
        return items.subList(fromIndex, toIndex);
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(SERVICE_ZONE).toInstant().toEpochMilli();
    }
}
