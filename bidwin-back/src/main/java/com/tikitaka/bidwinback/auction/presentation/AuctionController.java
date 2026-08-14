package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.AuctionCreateService;
import com.tikitaka.bidwinback.auction.application.AuctionDetailService;
import com.tikitaka.bidwinback.auction.application.AuctionListQuery;
import com.tikitaka.bidwinback.auction.application.AuctionListQuery.StatusFilter;
import com.tikitaka.bidwinback.auction.application.AuctionListService;
import com.tikitaka.bidwinback.auction.application.live.AuctionLiveStateService;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionSort;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.presentation.dto.request.AuctionCreateRequest;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionCreateResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionDetailResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionListResponse;
import com.tikitaka.bidwinback.global.auth.AuthMember;
import com.tikitaka.bidwinback.global.auth.Login;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final int FIRST_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 16;

    private final AuctionDetailService auctionDetailService;
    private final AuctionCreateService auctionCreateService;
    private final AuctionListService auctionListService;
    private final AuctionLiveStateService auctionLiveStateService;

    @GetMapping("/clock")
    public ResponseEntity<ApiResponse<Long>> getClock() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(auctionLiveStateService.getDatabaseTimeMillis()));
    }

    @GetMapping("/{auctionId}")
    public ResponseEntity<ApiResponse<AuctionDetailResponse>> getDetail(
            @PathVariable long auctionId
    ) {
        AuctionDetailResponse response = auctionDetailService.getDetail(auctionId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AuctionCreateResponse>> create(
            @Login AuthMember authMember,
            @Valid @RequestBody AuctionCreateRequest request
    ) {
        AuctionCreateResponse response = auctionCreateService.create(authMember.memberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AuctionListResponse>> getList(
            @RequestParam(required = false) String auctionType,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) List<String> category,
            @RequestParam(required = false, defaultValue = "" + FIRST_PAGE) int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            @RequestParam(required = false) Long asOf
    ) {
        AuctionListQuery query = new AuctionListQuery(
                parseAuctionType(auctionType),
                AuctionSort.from(sort),
                blankToNull(keyword),
                parseStatus(status),
                parseCategories(category),
                page,
                size,
                asOf != null ? toLocalDateTime(asOf) : null
        );

        AuctionListResponse response = auctionListService.getList(query);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private StatusFilter parseStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        try {
            return StatusFilter.valueOf(rawStatus);
        } catch (IllegalArgumentException exception) {
            throw new AuctionException(ErrorCode.INVALID_INPUT_VALUE, "지원하지 않는 경매 상태 필터입니다.");
        }
    }

    private List<AuctionCategory> parseCategories(List<String> rawCategories) {
        if (rawCategories == null) {
            return List.of();
        }
        return rawCategories.stream()
                .map(this::parseCategory)
                .toList();
    }

    private AuctionCategory parseCategory(String rawCategory) {
        try {
            return AuctionCategory.valueOf(rawCategory);
        } catch (IllegalArgumentException exception) {
            throw new AuctionException(ErrorCode.INVALID_INPUT_VALUE, "지원하지 않는 카테고리 필터입니다.");
        }
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
