package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.TradeConfirmationResult;
import com.tikitaka.bidwinback.auction.application.TradeConfirmationService;
import com.tikitaka.bidwinback.auction.presentation.dto.response.TradeConfirmationResponse;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.Login;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trades")
public class TradeConfirmationController {

    private final TradeConfirmationService tradeConfirmationService;

    @PostMapping("/{tradeId}/buyer-confirmation")
    public ResponseEntity<ApiResponse<TradeConfirmationResponse>> confirmBuyer(
            @Login AuthMember authMember,
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

    @PostMapping("/{tradeId}/seller-confirmation")
    public ResponseEntity<ApiResponse<TradeConfirmationResponse>> confirmSeller(
            @Login AuthMember authMember,
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
