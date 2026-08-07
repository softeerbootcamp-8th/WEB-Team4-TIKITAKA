package com.tikitaka.bidwinback.auction.application.live;

import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;

/**
 * 거래 화면 실시간 상태의 절대 스냅샷.
 * 연락처 같은 개인 정보는 담지 않는다. 상태가 바뀌면 클라이언트가 인증 조회로 상세를
 * 다시 읽어, 서버 게이팅을 통과한 값(구매자·CONFIRMED 이후에만 연락처)만 받도록 한다.
 */
public record TradeLiveState(
        long tradeId,
        long auctionId,
        TradeStatus status
) {
}
