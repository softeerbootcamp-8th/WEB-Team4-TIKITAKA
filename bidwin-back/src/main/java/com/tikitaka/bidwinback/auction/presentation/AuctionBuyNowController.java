package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.BuyNowResult;
import com.tikitaka.bidwinback.auction.application.BuyNowService;
import com.tikitaka.bidwinback.auction.presentation.dto.request.BuyNowRequest;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BuyNowResponse;
import com.tikitaka.bidwinback.global.auth.AuthConstant;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auctions")
public class AuctionBuyNowController {

    private final BuyNowService buyNowService;

    @PostMapping("/{auctionId}/buy-now")
    public ResponseEntity<ApiResponse<BuyNowResponse>> buyNow(
            @RequestAttribute(AuthConstant.REQUEST_ATTRIBUTE_KEY) AuthMember authMember,
            @PathVariable Long auctionId,
            @Valid @RequestBody BuyNowRequest request
    ) {
        BuyNowResult result = buyNowService.buy(
                authMember.memberId(),
                auctionId,
                request.idempotencyKey()
        );
        return ResponseEntity.ok(ApiResponse.success(BuyNowResponse.from(result)));
    }
}
