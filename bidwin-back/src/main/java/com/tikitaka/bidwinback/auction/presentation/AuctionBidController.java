package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.bid.BidHistoryService;
import com.tikitaka.bidwinback.auction.application.bid.BidResult;
import com.tikitaka.bidwinback.auction.application.bid.BidService;
import com.tikitaka.bidwinback.auction.presentation.dto.request.BidRequest;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidHistoryResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidResponse;
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
@Tag(name = "입찰", description = "상향 경매 입찰과 입찰 내역 조회")
public class AuctionBidController {

    private final BidService bidService;
    private final BidHistoryService bidHistoryService;

    @Operation(
            summary = "상향 경매 입찰",
            description = "현재가·호가 단위·보증금과 경매 상태를 검증한 뒤 입찰을 등록합니다.",
            security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SECURITY_SCHEME)
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "입찰 등록 완료",
            useReturnTypeSchema = true
    )
    @PostMapping("/up/{auctionId}/bids")
    public ResponseEntity<ApiResponse<BidResponse>> bid(
            @Login AuthMember authMember,
            @Parameter(description = "상향 경매 ID", example = "1")
            @PathVariable Long auctionId,
            @Valid @RequestBody BidRequest request
    ) {
        BidResult result = bidService.place(
                authMember.memberId(),
                auctionId,
                request.price(),
                request.bidType()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(BidResponse.from(result)));
    }

    @Operation(
            summary = "입찰 내역 조회",
            description = "경매의 입찰 내역을 최신순으로 조회합니다. 비공개 입찰은 진행 중 숨겨지고 경매 종료 후 공개됩니다."
    )
    @GetMapping("/{auctionId}/bids")
    public ResponseEntity<ApiResponse<BidHistoryResponse>> getBidHistory(
            @Parameter(description = "경매 ID", example = "1")
            @PathVariable long auctionId
    ) {
        BidHistoryResponse response = bidHistoryService.getBidHistory(auctionId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
