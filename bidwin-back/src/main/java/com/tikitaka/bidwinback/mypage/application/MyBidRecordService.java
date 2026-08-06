package com.tikitaka.bidwinback.mypage.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.BidRepository;
import com.tikitaka.bidwinback.auction.domain.repository.ImageRepository;
import com.tikitaka.bidwinback.auction.domain.repository.SealedBidRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionBidSummary;
import com.tikitaka.bidwinback.auction.domain.repository.dto.MyBidAggregate;
import com.tikitaka.bidwinback.global.common.PageResponse;
import com.tikitaka.bidwinback.global.storage.ImageUrlResolver;
import com.tikitaka.bidwinback.mypage.domain.RecordSort;
import com.tikitaka.bidwinback.mypage.domain.exception.MyPageException;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.MyBidRecordResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_INPUT_VALUE;

/*
 * 마이페이지 입찰 내역 — 다른 탭과 달리 "내가 입찰한, 아직 안 끝난 경매" 후보군이
 * 사람당 원래도 작아서(경매 목록 전체 규모가 아님) DB LIMIT/OFFSET 대신 자바에서
 * 계산·정렬·페이지를 자른다. AuctionListService 초기 버전과 같은 접근이다.
 */
@Service
@RequiredArgsConstructor
public class MyBidRecordService {

    private static final long SEALED_BID_WINDOW_MINUTES = 5L;
    private static final int FIRST_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final String WINNING_FILTER = "WINNING";
    private static final String LOSING_FILTER = "LOSING";

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final SealedBidRepository sealedBidRepository;
    private final ImageRepository imageRepository;
    private final ImageUrlResolver imageUrlResolver;

    @Transactional(readOnly = true)
    public PageResponse<MyBidRecordResponse> getBids(
            long memberId,
            String statusFilter,
            int pageNumber,
            int size,
            String sort
    ) {
        Map<Long, MyBidAggregate> myAggregatesByAuctionId = mergeMyAggregates(memberId);
        if (myAggregatesByAuctionId.isEmpty()) {
            return emptyPage(pageNumber, size);
        }

        LocalDateTime asOf = auctionRepository.currentDatabaseTime();
        List<Auction> activeAuctions = auctionRepository.findAllById(myAggregatesByAuctionId.keySet())
                .stream()
                .filter(auction -> isStillBiddable(auction, asOf))
                .toList();
        if (activeAuctions.isEmpty()) {
            return emptyPage(pageNumber, size);
        }

        List<Long> auctionIds = activeAuctions.stream().map(Auction::getId).toList();
        Map<Long, Long> overallHighestByAuctionId = fetchOverallHighest(auctionIds, asOf);
        Map<Long, String> thumbnails = fetchThumbnails(activeAuctions);

        List<MyBidRecordResponse> records = activeAuctions.stream()
                .map(auction -> toResponse(
                        auction,
                        myAggregatesByAuctionId.get(auction.getId()),
                        overallHighestByAuctionId.get(auction.getId()),
                        asOf,
                        thumbnails
                ))
                .filter(record -> matchesStatusFilter(record, statusFilter))
                .sorted(comparatorFor(RecordSort.from(sort)))
                .toList();

        return slice(records, pageNumber, size);
    }

    private Map<Long, MyBidAggregate> mergeMyAggregates(long memberId) {
        Map<Long, MyBidAggregate> merged = new HashMap<>();
        for (MyBidAggregate aggregate : bidRepository.summarizeMyBidsByMemberId(memberId)) {
            merged.merge(aggregate.auctionId(), aggregate, this::higherOf);
        }
        for (MyBidAggregate aggregate : sealedBidRepository.summarizeMySealedBidsByMemberId(memberId)) {
            merged.merge(aggregate.auctionId(), aggregate, this::higherOf);
        }
        return merged;
    }

    private MyBidAggregate higherOf(MyBidAggregate a, MyBidAggregate b) {
        return new MyBidAggregate(
                a.auctionId(),
                Math.max(a.myHighestPrice(), b.myHighestPrice()),
                a.myLastBidAt().isAfter(b.myLastBidAt()) ? a.myLastBidAt() : b.myLastBidAt()
        );
    }

    private boolean isStillBiddable(Auction auction, LocalDateTime asOf) {
        return (auction.getStatus() == AuctionStatus.OPEN || auction.getStatus() == AuctionStatus.BID_ONGOING)
                && auction.getEndedAt().isAfter(asOf);
    }

    // BidService.currentBidType()과 같은 경계(마감 5분 전)를 쓴다. 그쪽은 판정용이고
    // 여긴 "밀봉입찰중" 표시용이라 목적은 다르지만, 기준 자체는 같아야 서로 안 어긋난다.
    private boolean isSealedPhase(Auction auction, LocalDateTime asOf) {
        LocalDateTime sealedBidStartedAt = auction.getEndedAt().minusMinutes(SEALED_BID_WINDOW_MINUTES);
        return !asOf.isBefore(sealedBidStartedAt);
    }

    private Map<Long, Long> fetchOverallHighest(List<Long> auctionIds, LocalDateTime asOf) {
        Map<Long, Long> highest = new HashMap<>();
        for (AuctionBidSummary summary : bidRepository.summarizeByAuctionIds(auctionIds, asOf)) {
            if (summary.highestPrice() != null) {
                highest.merge(summary.auctionId(), summary.highestPrice(), Math::max);
            }
        }
        for (AuctionBidSummary summary : sealedBidRepository.summarizeSealedByAuctionIds(auctionIds)) {
            if (summary.highestPrice() != null) {
                highest.merge(summary.auctionId(), summary.highestPrice(), Math::max);
            }
        }
        return highest;
    }

    private Map<Long, String> fetchThumbnails(List<Auction> auctions) {
        List<Long> auctionIds = auctions.stream().map(Auction::getId).toList();
        return imageRepository.findFirstImageByAuctionIds(auctionIds).stream()
                .collect(Collectors.toMap(
                        image -> image.getAuction().getId(),
                        image -> imageUrlResolver.resolve(image.getObjectKey())));
    }

    private MyBidRecordResponse toResponse(
            Auction auction,
            MyBidAggregate myAggregate,
            Long overallHighestPrice,
            LocalDateTime asOf,
            Map<Long, String> thumbnails
    ) {
        long myHighest = myAggregate.myHighestPrice();
        long overallHighest = overallHighestPrice != null ? overallHighestPrice : myHighest;

        return new MyBidRecordResponse(
                auction.getId(),
                auction.getTitle(),
                thumbnails.get(auction.getId()),
                myHighest,
                toEpochMilli(auction.getEndedAt()),
                myHighest >= overallHighest,
                isSealedPhase(auction, asOf),
                toEpochMilli(myAggregate.myLastBidAt())
        );
    }

    private boolean matchesStatusFilter(MyBidRecordResponse record, String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) {
            return true;
        }
        if (statusFilter.equals(WINNING_FILTER)) {
            return record.isWinning();
        }
        if (statusFilter.equals(LOSING_FILTER)) {
            return !record.isWinning();
        }
        throw new MyPageException(INVALID_INPUT_VALUE, "지원하지 않는 입찰 상태 필터입니다.");
    }

    private Comparator<MyBidRecordResponse> comparatorFor(RecordSort sort) {
        Comparator<MyBidRecordResponse> byBiddedAt = Comparator.comparingLong(MyBidRecordResponse::biddedAt);
        return sort == RecordSort.OLDEST ? byBiddedAt : byBiddedAt.reversed();
    }

    private PageResponse<MyBidRecordResponse> slice(List<MyBidRecordResponse> items, int pageNumber, int size) {
        int safeSize = size > 0 ? size : DEFAULT_PAGE_SIZE;
        int totalPages = Math.max(FIRST_PAGE, (int) Math.ceil((double) items.size() / safeSize));
        int currentPage = Math.min(Math.max(FIRST_PAGE, pageNumber), totalPages);

        int fromIndex = Math.min((currentPage - FIRST_PAGE) * safeSize, items.size());
        int toIndex = Math.min(fromIndex + safeSize, items.size());

        return new PageResponse<>(items.subList(fromIndex, toIndex), currentPage, totalPages, items.size());
    }

    private PageResponse<MyBidRecordResponse> emptyPage(int pageNumber, int size) {
        return new PageResponse<>(List.of(), Math.max(FIRST_PAGE, pageNumber), FIRST_PAGE, 0);
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(SERVICE_ZONE).toInstant().toEpochMilli();
    }
}
