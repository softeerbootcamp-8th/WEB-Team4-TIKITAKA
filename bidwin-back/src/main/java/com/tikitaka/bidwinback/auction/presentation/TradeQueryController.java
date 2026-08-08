package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.TradeQueryService;
import com.tikitaka.bidwinback.auction.presentation.dto.response.TradeDetailResponse;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.Login;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trades")
public class TradeQueryController {

    private final TradeQueryService tradeQueryService;

    @GetMapping("/{tradeId}")
    public ResponseEntity<ApiResponse<TradeDetailResponse>> getTradeDetail(
            @Login AuthMember authMember,
            @PathVariable Long tradeId
    ) {
        TradeDetailResponse response = tradeQueryService.getTradeDetail(
                authMember.memberId(),
                tradeId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
