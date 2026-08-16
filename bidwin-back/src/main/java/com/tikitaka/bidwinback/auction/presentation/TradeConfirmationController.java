package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.TradeConfirmationResult;
import com.tikitaka.bidwinback.auction.application.TradeConfirmationService;
import com.tikitaka.bidwinback.auction.presentation.dto.response.TradeConfirmationResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trades")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SECURITY_SCHEME)
@Tag(name = "거래", description = "낙찰 거래 조회와 구매자·판매자 확인")
public class TradeConfirmationController {

    private final TradeConfirmationService tradeConfirmationService;

    @Operation(summary = "구매자 거래 확인", description = "거래 참여자인 구매자가 수령을 확인합니다. 양측 확인이 끝나면 거래가 완료됩니다.")
    @PostMapping("/{tradeId}/buyer-confirmation")
    public ResponseEntity<ApiResponse<TradeConfirmationResponse>> confirmBuyer(
            @Login AuthMember authMember,
            @Parameter(description = "거래 ID", example = "1")
            @PathVariable Long tradeId
    ) {
        TradeConfirmationResult result = tradeConfirmationService.confirmBuyer(
                authMember.memberId(),
                tradeId
        );
        return ResponseEntity.ok(
                ApiResponse.success(TradeConfirmationResponse.from(result))
        );
    }

    @Operation(summary = "판매자 거래 확인", description = "거래 참여자인 판매자가 전달을 확인합니다. 양측 확인이 끝나면 거래가 완료됩니다.")
    @PostMapping("/{tradeId}/seller-confirmation")
    public ResponseEntity<ApiResponse<TradeConfirmationResponse>> confirmSeller(
            @Login AuthMember authMember,
            @Parameter(description = "거래 ID", example = "1")
            @PathVariable Long tradeId
    ) {
        TradeConfirmationResult result = tradeConfirmationService.confirmSeller(
                authMember.memberId(),
                tradeId
        );
        return ResponseEntity.ok(
                ApiResponse.success(TradeConfirmationResponse.from(result))
        );
    }
}
