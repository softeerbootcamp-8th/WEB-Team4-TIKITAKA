package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.AuctionDetailService;
import com.tikitaka.bidwinback.auction.application.AuctionListQuery;
import com.tikitaka.bidwinback.auction.application.AuctionListService;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionDetailResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionListResponse;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final int FIRST_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 16;

    private final AuctionDetailService auctionDetailService;
    private final AuctionListService auctionListService;

    @GetMapping("/{auctionId}")
    public ResponseEntity<ApiResponse<AuctionDetailResponse>> getDetail(
            @PathVariable long auctionId
    ) {
        AuctionDetailResponse response = auctionDetailService.getDetail(auctionId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AuctionListResponse>> getList(
            @RequestParam(required = false) String auctionType,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "" + FIRST_PAGE) int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            @RequestParam(required = false) Long asOf
    ) {
        AuctionListQuery query = new AuctionListQuery(
                parseAuctionType(auctionType),
                AuctionSort.from(sort),
                blankToNull(keyword),
                page,
                size,
                asOf != null ? toLocalDateTime(asOf) : null
        );

        AuctionListResponse response = auctionListService.getList(query);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private AuctionType parseAuctionType(String rawAuctionType) {
        if (rawAuctionType == null || rawAuctionType.isBlank()) {
            return null;
        }
        try {
            return AuctionType.valueOf(rawAuctionType);
        } catch (IllegalArgumentException exception) {
            throw new AuctionException(ErrorCode.INVALID_INPUT_VALUE, "지원하지 않는 경매 타입입니다.");
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private LocalDateTime toLocalDateTime(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), SERVICE_ZONE);
    }
}
