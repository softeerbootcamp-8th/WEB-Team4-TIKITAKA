package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.BidRepository;
import com.tikitaka.bidwinback.auction.domain.repository.SealedBidRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.BidHistoryRow;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidHistoryItemResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidHistoryResponse;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class BidHistoryService {

    private static final int BID_HISTORY_LIMIT = 10;
    private static final String BID_ENTRY_PREFIX = "BID:";
    private static final String SEALED_ENTRY_PREFIX = "SEALED:";
    private static final Comparator<BidHistoryItemResponse> RECENT_BID_FIRST = Comparator
            .comparingLong(BidHistoryItemResponse::biddedAt)
            .reversed()
            .thenComparing(BidHistoryItemResponse::entryId, Comparator.reverseOrder());

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final SealedBidRepository sealedBidRepository;

    @Transactional(readOnly = true)
    public BidHistoryResponse getBidHistory(long auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));

        AuctionStatus status = auction.getStatus();
        long bidCount = auction.getBidCount();
        if (isSealedBidRevealed(status)) {
            bidCount += auction.getSealedBidCount();
        }
        return getBidHistory(auctionId, status, bidCount);
    }

    /** SSE 초기 상태에서 이미 검증한 경매 상태와 입찰 수를 재사용한다. */
    @Transactional(readOnly = true)
    public BidHistoryResponse getBidHistory(
            long auctionId,
            AuctionStatus status,
            long bidCount
    ) {
        Stream<BidHistoryItemResponse> bidHistory = bidRepository.findHistoryByAuctionId(auctionId)
                .stream()
                .map(row -> toResponse(row, BID_ENTRY_PREFIX));
        if (isSealedBidRevealed(status)) {
            bidHistory = mergeWithSealedBids(auctionId, bidHistory);
        }

        List<BidHistoryItemResponse> bidLog = bidHistory
                .sorted(RECENT_BID_FIRST)
                .limit(BID_HISTORY_LIMIT)
                .toList();

        return new BidHistoryResponse(bidCount, bidLog);
    }

    private boolean isSealedBidRevealed(AuctionStatus status) {
        return status == AuctionStatus.WINNER_DETERMINING
                || status == AuctionStatus.COMPLETED
                || status == AuctionStatus.UNSOLD;
    }

    private Stream<BidHistoryItemResponse> mergeWithSealedBids(
            long auctionId,
            Stream<BidHistoryItemResponse> openBids
    ) {
        Stream<BidHistoryItemResponse> sealedBids = sealedBidRepository
                .findHistoryByAuctionId(auctionId)
                .stream()
                .map(row -> toResponse(row, SEALED_ENTRY_PREFIX));

        return Stream.concat(openBids, sealedBids);
    }

    private BidHistoryItemResponse toResponse(BidHistoryRow bid, String entryPrefix) {
        return BidHistoryItemResponse.of(
                entryPrefix + bid.id(),
                bid.bidderNickname(),
                bid.amount(),
                bid.biddedAt()
        );
    }
}
