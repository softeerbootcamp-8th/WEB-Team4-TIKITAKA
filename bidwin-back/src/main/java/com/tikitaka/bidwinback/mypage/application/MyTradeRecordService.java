package com.tikitaka.bidwinback.mypage.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionTradeRepository;
import com.tikitaka.bidwinback.auction.domain.repository.ImageRepository;
import com.tikitaka.bidwinback.global.common.PageResponse;
import com.tikitaka.bidwinback.global.storage.ImageUrlResolver;
import com.tikitaka.bidwinback.mypage.domain.RecordPageRequest;
import com.tikitaka.bidwinback.mypage.domain.RecordSort;
import com.tikitaka.bidwinback.mypage.domain.StatusFilters;
import com.tikitaka.bidwinback.mypage.domain.TradeRoute;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.MyTradeRecordResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 낙찰 내역/구매 내역 두 탭이 같은 AuctionTrade(구매자=나)를 조회한다.
 * "낙찰 내역"은 status 필터 없이 전체 진행 단계를, "구매 내역"은 COMPLETED로 좁혀서 쓴다.
 */
@Service
@RequiredArgsConstructor
public class MyTradeRecordService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final AuctionTradeRepository auctionTradeRepository;
    private final ImageRepository imageRepository;
    private final ImageUrlResolver imageUrlResolver;

    @Transactional(readOnly = true)
    public PageResponse<MyTradeRecordResponse> getTrades(
            long memberId,
            String statusFilter,
            int pageNumber,
            int size,
            String sort
    ) {
        List<TradeStatus> statuses = StatusFilters.resolve(TradeStatus.class, statusFilter);
        Pageable pageable = RecordPageRequest.of(pageNumber, size, "purchasedAt", RecordSort.from(sort));

        Page<AuctionTrade> page = auctionTradeRepository.findByBuyerIdAndStatusIn(
                memberId,
                statuses,
                pageable
        );
        List<AuctionTrade> trades = page.getContent();

        Map<Long, String> thumbnails = fetchThumbnails(trades);

        List<MyTradeRecordResponse> items = trades.stream()
                .map(trade -> toResponse(trade, thumbnails))
                .toList();

        return PageResponse.from(page, items);
    }

    private Map<Long, String> fetchThumbnails(List<AuctionTrade> trades) {
        List<Long> auctionIds = trades.stream().map(trade -> trade.getAuction().getId()).toList();
        if (auctionIds.isEmpty()) {
            return Map.of();
        }
        return imageRepository.findFirstImageByAuctionIds(auctionIds).stream()
                .collect(Collectors.toMap(
                        image -> image.getAuction().getId(),
                        image -> imageUrlResolver.resolve(image.getObjectKey())));
    }

    private MyTradeRecordResponse toResponse(AuctionTrade trade, Map<Long, String> thumbnails) {
        Auction auction = trade.getAuction();
        return new MyTradeRecordResponse(
                auction.getId(),
                auction.getTitle(),
                thumbnails.get(auction.getId()),
                trade.getFinalPrice(),
                trade.getPurchasedAt().atZone(SERVICE_ZONE).toInstant().toEpochMilli(),
                trade.getStatus(),
                TradeRoute.from(AuctionType.from(auction))
        );
    }
}
