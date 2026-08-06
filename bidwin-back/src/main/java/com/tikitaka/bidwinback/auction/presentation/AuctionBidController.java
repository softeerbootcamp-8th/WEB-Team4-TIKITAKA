package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.BidHistoryService;
import com.tikitaka.bidwinback.auction.application.BidResult;
import com.tikitaka.bidwinback.auction.application.BidService;
import com.tikitaka.bidwinback.auction.presentation.dto.request.BidRequest;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidHistoryResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidResponse;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.Login;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auctions")
public class AuctionBidController {

    private final BidService bidService;
    private final BidHistoryService bidHistoryService;

    @PostMapping("/up/{auctionId}/bids")
    public ResponseEntity<ApiResponse<BidResponse>> bid(
            @Login AuthMember authMember,
            @PathVariable Long auctionId,
            @Valid @RequestBody BidRequest request
    ) {
        BidResult result = bidService.place(
                authMember.memberId(),
                auctionId,
                request.status(),
                request.price()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(BidResponse.from(result)));
    }

    @GetMapping("/{auctionId}/bids")
    public ResponseEntity<ApiResponse<BidHistoryResponse>> getBidHistory(
            @PathVariable long auctionId,
            @Login AuthMember authMember
    ) {
        BidHistoryResponse response = bidHistoryService.getBidHistory(
                auctionId,
                authMember.memberId()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
