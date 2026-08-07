package com.tikitaka.bidwinback.mypage.domain;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.mypage.domain.exception.MyPageException;

import java.util.Arrays;
import java.util.List;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_INPUT_VALUE;

/**
 * 마이페이지 판매 내역 탭의 상태 필터. 경매 상태(AuctionStatus)는 5가지지만,
 * 판매자 입장에선 "판매 중 / 낙찰 완료 / 유찰" 3가지로만 구분해서 보여주면 된다.
 */
public enum SellingStatusFilter {
    ON_SALE(List.of(AuctionStatus.OPEN, AuctionStatus.BID_ONGOING, AuctionStatus.WINNER_DETERMINING)),
    SOLD(List.of(AuctionStatus.COMPLETED)),
    FAILED(List.of(AuctionStatus.UNSOLD));

    private static final List<AuctionStatus> ALL_STATUSES = List.of(AuctionStatus.values());

    private final List<AuctionStatus> statuses;

    SellingStatusFilter(List<AuctionStatus> statuses) {
        this.statuses = statuses;
    }

    public static List<AuctionStatus> statusesOf(String code) {
        if (code == null || code.isBlank()) {
            return ALL_STATUSES;
        }
        return Arrays.stream(values())
                .filter(filter -> filter.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new MyPageException(INVALID_INPUT_VALUE, "지원하지 않는 판매 상태 필터입니다."))
                .statuses;
    }
}
