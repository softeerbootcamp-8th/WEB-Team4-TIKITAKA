package com.tikitaka.bidwinback.mypage.presentation;

import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.Login;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import com.tikitaka.bidwinback.global.common.PageResponse;
import com.tikitaka.bidwinback.global.config.OpenApiConfig;
import com.tikitaka.bidwinback.mypage.application.MyBidRecordService;
import com.tikitaka.bidwinback.mypage.application.MyDepositRecordService;
import com.tikitaka.bidwinback.mypage.application.MySaleRecordService;
import com.tikitaka.bidwinback.mypage.application.MyTradeRecordService;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.MyBidRecordResponse;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.MyDepositRecordResponse;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.MySaleRecordResponse;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.MyTradeRecordResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SECURITY_SCHEME)
@Tag(name = "마이페이지", description = "내 정보와 활동 내역 관리")
public class MyPageHistoryController {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;

    private final MyBidRecordService myBidRecordService;
    private final MySaleRecordService mySaleRecordService;
    private final MyTradeRecordService myTradeRecordService;
    private final MyDepositRecordService myDepositRecordService;

    @Operation(summary = "내 입찰 내역 조회", description = "로그인 회원이 참여한 경매별 최고 입찰과 현재 낙찰 우위 여부를 조회합니다.")
    @GetMapping("/bids")
    public ResponseEntity<ApiResponse<PageResponse<MyBidRecordResponse>>> getBids(
            @Login AuthMember authMember,
            @Parameter(description = "입찰 상태", example = "WINNING", schema = @Schema(allowableValues = {"WINNING", "LOSING"}))
            @RequestParam(required = false) String status,
            @Parameter(description = "페이지 번호. 1부터 시작", example = "1")
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE) int page,
            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(required = false, defaultValue = "" + DEFAULT_SIZE) int size,
            @Parameter(description = "정렬 방향. 기본값은 latest", example = "latest", schema = @Schema(allowableValues = {"latest", "oldest"}))
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

    @Operation(summary = "내 판매 내역 조회", description = "로그인 회원이 등록한 경매와 판매 상태를 조회합니다.")
    @GetMapping("/sales")
    public ResponseEntity<ApiResponse<PageResponse<MySaleRecordResponse>>> getSales(
            @Login AuthMember authMember,
            @Parameter(description = "판매 상태", example = "ON_SALE", schema = @Schema(allowableValues = {"ON_SALE", "SOLD", "FAILED"}))
            @RequestParam(required = false) String status,
            @Parameter(description = "페이지 번호. 1부터 시작", example = "1")
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE) int page,
            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(required = false, defaultValue = "" + DEFAULT_SIZE) int size,
            @Parameter(description = "정렬 방향. 기본값은 latest", example = "latest", schema = @Schema(allowableValues = {"latest", "oldest"}))
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
    @Operation(summary = "내 낙찰·구매 내역 조회", description = "로그인 회원이 구매자로 참여한 거래를 조회합니다. 구매 완료 내역은 status=COMPLETED로 조회합니다.")
    @GetMapping("/trades")
    public ResponseEntity<ApiResponse<PageResponse<MyTradeRecordResponse>>> getTrades(
            @Login AuthMember authMember,
            @Parameter(
                    description = "거래 상태",
                    example = "COMPLETED",
                    schema = @Schema(allowableValues = {"WAITING_CONFIRM", "CONFIRMED", "COMPLETED", "BUYER_FAILED", "SELLER_FAILED"})
            )
            @RequestParam(required = false) String status,
            @Parameter(description = "페이지 번호. 1부터 시작", example = "1")
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE) int page,
            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(required = false, defaultValue = "" + DEFAULT_SIZE) int size,
            @Parameter(description = "정렬 방향. 기본값은 latest", example = "latest", schema = @Schema(allowableValues = {"latest", "oldest"}))
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

    @Operation(summary = "내 보증금 내역 조회", description = "로그인 회원의 경매별 보증금 보류·환불·몰수·사용 내역을 조회합니다.")
    @GetMapping("/deposits")
    public ResponseEntity<ApiResponse<PageResponse<MyDepositRecordResponse>>> getDeposits(
            @Login AuthMember authMember,
            @Parameter(
                    description = "보증금 상태",
                    example = "HELD",
                    schema = @Schema(allowableValues = {"HELD", "REFUNDED", "FORFEITED", "USED"})
            )
            @RequestParam(required = false) String status,
            @Parameter(description = "페이지 번호. 1부터 시작", example = "1")
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE) int page,
            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(required = false, defaultValue = "" + DEFAULT_SIZE) int size,
            @Parameter(description = "정렬 방향. 기본값은 latest", example = "latest", schema = @Schema(allowableValues = {"latest", "oldest"}))
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
