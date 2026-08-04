package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.AuctionCreateService;
import com.tikitaka.bidwinback.auction.application.AuctionDetailService;
import com.tikitaka.bidwinback.auction.presentation.dto.request.AuctionCreateRequest;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionCreateResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionDetailResponse;
import com.tikitaka.bidwinback.global.auth.AuthConstant;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionDetailService auctionDetailService;
    private final AuctionCreateService auctionCreateService;

    @GetMapping("/{auctionId}")
    public ResponseEntity<ApiResponse<AuctionDetailResponse>> getDetail(
            @PathVariable long auctionId
    ) {
        AuctionDetailResponse response = auctionDetailService.getDetail(auctionId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AuctionCreateResponse>> create(
            @RequestAttribute(AuthConstant.REQUEST_ATTRIBUTE_KEY) AuthMember authMember,
            @Valid @RequestBody AuctionCreateRequest request
    ) {
        AuctionCreateResponse response = auctionCreateService.create(authMember.memberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
