package com.tikitaka.bidwinback.mypage.presentation;

import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.Login;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import com.tikitaka.bidwinback.global.common.PageResponse;
import com.tikitaka.bidwinback.mypage.application.MyBidRecordService;
import com.tikitaka.bidwinback.mypage.application.MyDepositRecordService;
import com.tikitaka.bidwinback.mypage.application.MySaleRecordService;
import com.tikitaka.bidwinback.mypage.application.MyTradeRecordService;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.MyBidRecordResponse;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.MyDepositRecordResponse;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.MySaleRecordResponse;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.MyTradeRecordResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/*
 * 마이페이지 "내 활동 기록" 5개 탭 중 낙찰/구매는 같은 거래 데이터를 상태 필터만
 * 다르게 써서 하나의 서비스(MyTradeRecordService)를 공유한다. 나머지 3개(입찰/판매/보증금)는
 * 근거 테이블이 서로 달라 탭마다 별도 서비스를 둔다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/mypage")
public class MyPageHistoryController {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;

    private final MyBidRecordService myBidRecordService;
    private final MySaleRecordService mySaleRecordService;
    private final MyTradeRecordService myTradeRecordService;
    private final MyDepositRecordService myDepositRecordService;

    @GetMapping("/bids")
    public ResponseEntity<ApiResponse<PageResponse<MyBidRecordResponse>>> getBids(
            @Login AuthMember authMember,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE) int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_SIZE) int size,
            @RequestParam(required = false) String sort
    ) {
        PageResponse<MyBidRecordResponse> response = myBidRecordService.getBids(
                authMember.memberId(),
                blankToNull(status),
                page,
                size,
                sort
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/sales")
    public ResponseEntity<ApiResponse<PageResponse<MySaleRecordResponse>>> getSales(
            @Login AuthMember authMember,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE) int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_SIZE) int size,
            @RequestParam(required = false) String sort
    ) {
        PageResponse<MySaleRecordResponse> response = mySaleRecordService.getSales(
                authMember.memberId(),
                blankToNull(status),
                page,
                size,
                sort
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 낙찰 내역: 진행 단계 전체를 보여준다(status 필터 없이 호출).
    // 구매 내역: status=COMPLETED로 좁혀서 같은 엔드포인트를 호출한다.
    @GetMapping("/trades")
    public ResponseEntity<ApiResponse<PageResponse<MyTradeRecordResponse>>> getTrades(
            @Login AuthMember authMember,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE) int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_SIZE) int size,
            @RequestParam(required = false) String sort
    ) {
        PageResponse<MyTradeRecordResponse> response = myTradeRecordService.getTrades(
                authMember.memberId(),
                blankToNull(status),
                page,
                size,
                sort
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/deposits")
    public ResponseEntity<ApiResponse<PageResponse<MyDepositRecordResponse>>> getDeposits(
            @Login AuthMember authMember,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE) int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_SIZE) int size,
            @RequestParam(required = false) String sort
    ) {
        PageResponse<MyDepositRecordResponse> response = myDepositRecordService.getDeposits(
                authMember.memberId(),
                blankToNull(status),
                page,
                size,
                sort
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
