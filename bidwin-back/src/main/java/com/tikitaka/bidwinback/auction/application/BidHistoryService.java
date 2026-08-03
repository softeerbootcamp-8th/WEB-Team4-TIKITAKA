package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.BidRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.BidHistoryRow;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidHistoryItemResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidHistoryResponse;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BidHistoryService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;

    @Transactional(readOnly = true)
    public BidHistoryResponse getBidHistory(long auctionId, long memberId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));

        List<BidHistoryItemResponse> bidLog = bidRepository.findHistoryByAuctionId(auctionId)
                .stream()
                .map(bid -> toResponse(bid, memberId))
                .toList();

        return new BidHistoryResponse(bidLog.size(), bidLog);
    }

    private BidHistoryItemResponse toResponse(BidHistoryRow bid, long memberId) {
        boolean isMe = Objects.equals(bid.bidderId(), memberId);

        return new BidHistoryItemResponse(
                bid.id(),
                isMe ? "나" : maskNickname(bid.bidderNickname()),
                bid.amount(),
                toEpochMilli(bid.biddedAt()),
                isMe
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
