package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.AuctionDetailService;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionDetailResponse;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionDetailService auctionDetailService;

    @GetMapping("/{auctionId}")
    public ResponseEntity<ApiResponse<AuctionDetailResponse>> getDetail(
            @PathVariable long auctionId
    ) {
        AuctionDetailResponse response = auctionDetailService.getDetail(auctionId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
