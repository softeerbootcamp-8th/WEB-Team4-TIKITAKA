package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.buynow.BuyNowResult;
import com.tikitaka.bidwinback.auction.application.buynow.BuyNowService;
import com.tikitaka.bidwinback.auction.presentation.dto.request.BuyNowRequest;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BuyNowResponse;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.Login;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import com.tikitaka.bidwinback.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auctions")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SECURITY_SCHEME)
@Tag(name = "즉시구매", description = "상향·하향 경매 즉시구매")
public class AuctionBuyNowController {

    private final BuyNowService buyNowService;

    @Operation(summary = "상향 경매 즉시구매", description = "설정된 즉시구매가로 상향 경매의 구매를 확정합니다. 같은 멱등 키 재시도는 기존 결과를 반환합니다.")
    @PostMapping("/up/{auctionId}/buy-now")
    public ResponseEntity<ApiResponse<BuyNowResponse>> buyUpAuction(
            @Login AuthMember authMember,
            @Parameter(description = "상향 경매 ID", example = "1")
            @PathVariable Long auctionId,
            @Valid @RequestBody BuyNowRequest request
    ) {
        BuyNowResult result = buyNowService.buyUpAuction(
                authMember.memberId(),
                auctionId,
                request.idempotencyKey()
        );
        return ResponseEntity.ok(ApiResponse.success(BuyNowResponse.from(result)));
    }

    @Operation(summary = "하향 경매 즉시구매", description = "요청 시점의 현재가로 하향 경매의 구매를 확정합니다. 같은 멱등 키 재시도는 기존 결과를 반환합니다.")
    @PostMapping("/down/{auctionId}/buy-now")
    public ResponseEntity<ApiResponse<BuyNowResponse>> buyDownAuction(
            @Login AuthMember authMember,
            @Parameter(description = "하향 경매 ID", example = "1")
            @PathVariable Long auctionId,
            @Valid @RequestBody BuyNowRequest request
    ) {
        BuyNowResult result = buyNowService.buyDownAuction(
                authMember.memberId(),
                auctionId,
                request.idempotencyKey()
        );
        return ResponseEntity.ok(ApiResponse.success(BuyNowResponse.from(result)));
    }
}
