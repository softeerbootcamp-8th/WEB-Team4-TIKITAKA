package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionListQueryRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListRow;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionListSearchCondition;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceCursor;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionPriceSnapshot;
import com.tikitaka.bidwinback.auction.domain.repository.dto.DownAuctionPriceCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

@Component
@RequiredArgsConstructor
public class AuctionPricePageQuery {

    static final int CANDIDATE_BATCH_SIZE = 1_000;

    private final AuctionListQueryRepository auctionListQueryRepository;

    public List<AuctionListRow> findPage(
            AuctionListSearchCondition condition,
            int page,
            int size,
            long totalCount
    ) {
        int topKSize = topKSize(page, size, totalCount);
        if (topKSize == 0) {
            return List.of();
        }

        Comparator<AuctionPriceSnapshot> resultOrder = resultOrder(condition.sort());
        PriorityQueue<AuctionPriceSnapshot> topK = new PriorityQueue<>(
                Math.min(topKSize, CANDIDATE_BATCH_SIZE),
                resultOrder.reversed()
        );

        if (condition.auctionType() != AuctionType.DOWN) {
            auctionListQueryRepository.findUpPriceSnapshots(condition, topKSize)
                    .forEach(snapshot -> offer(topK, snapshot, topKSize, resultOrder));
        }
        if (condition.auctionType() != AuctionType.UP) {
            findDownTopK(condition, topKSize, resultOrder, topK);
        }

        List<AuctionPriceSnapshot> orderedTopK = topK.stream()
                .sorted(resultOrder)
                .toList();
        int fromIndex = (int) Math.min(
                (long) (page - 1) * size,
                orderedTopK.size()
        );
        int toIndex = Math.min(fromIndex + size, orderedTopK.size());
        return auctionListQueryRepository.findRowsByPriceSnapshots(
                orderedTopK.subList(fromIndex, toIndex),
                condition.asOf()
        );
    }

    private void findDownTopK(
            AuctionListSearchCondition condition,
            int topKSize,
            Comparator<AuctionPriceSnapshot> resultOrder,
            PriorityQueue<AuctionPriceSnapshot> topK
    ) {
        AuctionPriceCursor cursor = null;

        while (true) {
            List<DownAuctionPriceCandidate> candidates = auctionListQueryRepository
                    .findDownPriceCandidates(condition, cursor, CANDIDATE_BATCH_SIZE);
            if (candidates.isEmpty()) {
                break;
            }

            candidates.forEach(candidate -> offer(
                    topK,
                    snapshotAt(candidate, condition),
                    topKSize,
                    resultOrder
            ));

            DownAuctionPriceCandidate lastCandidate = candidates.getLast();
            long remainingPriceBound = priceBound(lastCandidate, condition.sort());
            if (candidates.size() < CANDIDATE_BATCH_SIZE
                    || canDiscardRemaining(
                            topK,
                            topKSize,
                            remainingPriceBound,
                            condition.sort()
                    )) {
                break;
            }
            cursor = new AuctionPriceCursor(
                    remainingPriceBound,
                    lastCandidate.auctionId()
            );
        }
    }

    private int topKSize(int page, int size, long totalCount) {
        long requested = Math.multiplyExact((long) page, size);
        long required = Math.min(totalCount, requested);
        if (required > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("가격순으로 조회할 페이지 범위가 너무 큽니다.");
        }
        return (int) required;
    }

    private AuctionPriceSnapshot snapshotAt(
            DownAuctionPriceCandidate candidate,
            AuctionListSearchCondition condition
    ) {
        return new AuctionPriceSnapshot(
                candidate.auctionId(),
                candidate.currentPriceAt(condition.asOf())
        );
    }

    private void offer(
            PriorityQueue<AuctionPriceSnapshot> topK,
            AuctionPriceSnapshot candidate,
            int topKSize,
            Comparator<AuctionPriceSnapshot> resultOrder
    ) {
        if (topK.size() < topKSize) {
            topK.offer(candidate);
            return;
        }
        if (resultOrder.compare(candidate, topK.element()) < 0) {
            topK.remove();
            topK.offer(candidate);
        }
    }

    private boolean canDiscardRemaining(
            PriorityQueue<AuctionPriceSnapshot> topK,
            int topKSize,
            long remainingPriceBound,
            AuctionSort sort
    ) {
        if (topK.size() < topKSize) {
            return false;
        }

        long worstTopKPrice = topK.element().currentPrice();
        // 경계가 같은 뒤쪽 레코드는 id tie-break에 따라 Top-K에 들어올 수 있으므로
        // 등호에서는 다음 배치를 확인하고, 가격이 엄격히 앞설 때만 중단한다.
        return switch (sort) {
            case PRICE_LOW -> worstTopKPrice < remainingPriceBound;
            case PRICE_HIGH -> worstTopKPrice > remainingPriceBound;
            case RECOMMENDED, DEADLINE, LATEST ->
                    throw new IllegalArgumentException("가격 정렬이 아닙니다: " + sort);
        };
    }

    private long priceBound(DownAuctionPriceCandidate candidate, AuctionSort sort) {
        return switch (sort) {
            case PRICE_LOW -> candidate.minimumPrice();
            case PRICE_HIGH -> candidate.startPrice();
            case RECOMMENDED, DEADLINE, LATEST ->
                    throw new IllegalArgumentException("가격 정렬이 아닙니다: " + sort);
        };
    }

    private Comparator<AuctionPriceSnapshot> resultOrder(AuctionSort sort) {
        Comparator<AuctionPriceSnapshot> idDescending = Comparator
                .comparingLong(AuctionPriceSnapshot::auctionId)
                .reversed();
        return switch (sort) {
            case PRICE_LOW -> Comparator
                    .comparingLong(AuctionPriceSnapshot::currentPrice)
                    .thenComparing(idDescending);
            case PRICE_HIGH -> Comparator
                    .comparingLong(AuctionPriceSnapshot::currentPrice)
                    .reversed()
                    .thenComparing(idDescending);
            case RECOMMENDED, DEADLINE, LATEST ->
                    throw new IllegalArgumentException("가격 정렬이 아닙니다: " + sort);
        };
    }
}
