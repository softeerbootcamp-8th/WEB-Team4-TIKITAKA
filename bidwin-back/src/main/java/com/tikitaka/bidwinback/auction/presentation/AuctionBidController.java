package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.BidResult;
import com.tikitaka.bidwinback.auction.application.BidService;
import com.tikitaka.bidwinback.auction.presentation.dto.request.BidRequest;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidResponse;
import com.tikitaka.bidwinback.global.auth.AuthConstant;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
public class AuctionBidController {

    private final BidService bidService;

    // 하향 경매는 즉시구매로만 거래되므로 입찰 경로는 상향 경매 아래에만 둔다.
    @PostMapping("/up/{auctionId}/bids")
    public ResponseEntity<ApiResponse<BidResponse>> bid(
            @RequestAttribute(AuthConstant.REQUEST_ATTRIBUTE_KEY) AuthMember authMember,
            @PathVariable Long auctionId,
            @Valid @RequestBody BidRequest request
    ) {
        BidResult result = bidService.place(
                authMember.memberId(),
                auctionId,
                request.price()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(BidResponse.from(result)));
    }
}
