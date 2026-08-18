package com.tikitaka.bidwinback.auction.application.trade;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
import com.tikitaka.bidwinback.auction.domain.exception.TradeException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionTradeRepository;
import com.tikitaka.bidwinback.auction.domain.repository.ImageRepository;
import com.tikitaka.bidwinback.auction.presentation.dto.response.TradeDetailResponse;
import com.tikitaka.bidwinback.global.storage.ImageUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.TRADE_ACCESS_DENIED;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.TRADE_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class TradeQueryService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final String ROLE_BUYER = "BUYER";
    private static final String ROLE_SELLER = "SELLER";

    private final AuctionTradeRepository auctionTradeRepository;
    private final ImageRepository imageRepository;
    private final ImageUrlResolver imageUrlResolver;

    @Transactional(readOnly = true)
    public TradeDetailResponse getTradeDetail(Long memberId, Long tradeId) {
        AuctionTrade trade = findDetail(tradeId);
        boolean isBuyer = isBuyer(trade, memberId);
        // 구매자도 판매자도 아니면 남의 거래다. 존재 여부까지 감추도록 403으로 막는다.
        if (!isBuyer && !isSeller(trade, memberId)) {
            throw new TradeException(TRADE_ACCESS_DENIED);
        }

        Auction auction = trade.getAuction();
        return new TradeDetailResponse(
                trade.getId(),
                auction.getId(),
                auction.getTitle(),
                resolveThumbnailUrl(auction.getId()),
                AuctionType.from(auction),
                trade.getStatus(),
                isBuyer ? ROLE_BUYER : ROLE_SELLER,
                trade.getFinalPrice(),
                toEpochMilli(trade.getPurchasedAt()),
                revealedContact(trade, isBuyer)
        );
    }

    // SSE 구독 전 인가 검사. 구매자·판매자만 자신의 거래 채널을 열 수 있다(IDOR 차단).
    @Transactional(readOnly = true)
    public void verifyParticipant(Long memberId, Long tradeId) {
        AuctionTrade trade = findDetail(tradeId);
        if (!isBuyer(trade, memberId) && !isSeller(trade, memberId)) {
            throw new TradeException(TRADE_ACCESS_DENIED);
        }
    }

    private AuctionTrade findDetail(Long tradeId) {
        return auctionTradeRepository.findDetailById(tradeId)
                .orElseThrow(() -> new TradeException(TRADE_NOT_FOUND));
    }

    private boolean isBuyer(AuctionTrade trade, Long memberId) {
        return trade.getBuyer().getId().equals(memberId);
    }

    private boolean isSeller(AuctionTrade trade, Long memberId) {
        return trade.getAuction().getSeller().getId().equals(memberId);
    }

    /*
     * 연락처 공개 규칙: CONFIRMED 이후(구매자에게만) 판매자 연락처를 노출한다.
     * 그 외 상태나 판매자 요청에는 절대 담지 않는다(경매 정보 포함 어디에도 노출 금지).
     */
    private String revealedContact(AuctionTrade trade, boolean isBuyer) {
        if (!isBuyer) {
            return null;
        }
        TradeStatus status = trade.getStatus();
        boolean revealable = status == TradeStatus.CONFIRMED || status == TradeStatus.COMPLETED;
        return revealable ? trade.getAuction().getContact() : null;
    }

    private String resolveThumbnailUrl(long auctionId) {
        return imageRepository.findRepresentativeThumbnails(Set.of(auctionId)).stream()
                .findFirst()
                .map(row -> imageUrlResolver.resolve(row.objectKey()))
                .orElse(null);
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(SERVICE_ZONE).toInstant().toEpochMilli();
    }
}
