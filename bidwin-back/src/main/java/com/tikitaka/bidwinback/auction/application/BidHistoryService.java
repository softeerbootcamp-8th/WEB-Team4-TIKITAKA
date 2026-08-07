package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class BidHistoryService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
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

        long bidCount = bidRepository.countByAuctionId(auctionId);
        Stream<BidHistoryItemResponse> bidHistory = bidRepository.findHistoryByAuctionId(auctionId)
                .stream()
                .map(row -> toResponse(row, BID_ENTRY_PREFIX));
        if (auction.isSealedBidRevealed()) {
            bidCount += sealedBidRepository.countByAuctionId(auctionId);
            bidHistory = mergeWithSealedBids(auctionId, bidHistory);
        }

        List<BidHistoryItemResponse> bidLog = bidHistory
                .sorted(RECENT_BID_FIRST)
                .limit(BID_HISTORY_LIMIT)
                .toList();

        return new BidHistoryResponse(bidCount, bidLog);
    }

    /** 커밋된 일반 입찰 한 건을 공개 SSE DTO로 조회한다. */
    @Transactional(readOnly = true)
    public BidHistoryItemResponse getPublishedBid(long auctionId, long bidId) {
        BidHistoryRow row = bidRepository.findHistoryByIdAndAuctionId(bidId, auctionId)
                .orElseThrow(() -> new IllegalStateException(
                        "커밋된 입찰 내역을 찾을 수 없습니다. auctionId="
                                + auctionId + ", bidId=" + bidId
                ));
        return toResponse(row, BID_ENTRY_PREFIX);
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
        return new BidHistoryItemResponse(
                entryPrefix + bid.id(),
                maskNickname(bid.bidderNickname()),
                bid.amount(),
                toEpochMilli(bid.biddedAt())
        );
    }

    // 닉네임 마스킹
    private String maskNickname(String nickname) {
        int nicknameLength = nickname.length();
        if (nicknameLength <= 1) {
            return "*";
        }

        String firstCharacter = nickname.substring(0, 1);
        if (nicknameLength == 2) {
            return firstCharacter + "*";
        }

        String lastCharacter = nickname.substring(nicknameLength - 1);

        return firstCharacter
                + "*".repeat(nicknameLength - 2)
                + lastCharacter;
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(SERVICE_ZONE).toInstant().toEpochMilli();
    }
}
