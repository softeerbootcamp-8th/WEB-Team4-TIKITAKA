package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.trade.TradeQueryService;
import com.tikitaka.bidwinback.auction.presentation.dto.response.TradeDetailResponse;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.Login;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import com.tikitaka.bidwinback.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trades")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SECURITY_SCHEME)
@Tag(name = "거래", description = "낙찰 거래 조회와 구매자·판매자 확인")
public class TradeQueryController {

    private final TradeQueryService tradeQueryService;

    @Operation(summary = "거래 상세 조회", description = "거래 참여자만 상세를 조회할 수 있습니다. 연락처는 거래 상태와 사용자 역할에 따라 제한됩니다.")
    @GetMapping("/{tradeId}")
    public ResponseEntity<ApiResponse<TradeDetailResponse>> getTradeDetail(
            @Login AuthMember authMember,
            @Parameter(description = "거래 ID", example = "1")
            @PathVariable Long tradeId
    ) {
        TradeDetailResponse response = tradeQueryService.getTradeDetail(
                authMember.memberId(),
                tradeId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
