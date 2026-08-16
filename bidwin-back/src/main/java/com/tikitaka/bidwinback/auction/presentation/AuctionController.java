package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.AuctionCreateService;
import com.tikitaka.bidwinback.auction.application.AuctionDetailService;
import com.tikitaka.bidwinback.auction.application.AuctionListQuery;
import com.tikitaka.bidwinback.auction.application.AuctionListQuery.StatusFilter;
import com.tikitaka.bidwinback.auction.application.AuctionListService;
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
import com.tikitaka.bidwinback.global.config.OpenApiConfig;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
@Tag(name = "경매", description = "경매 등록과 목록·상세 조회")
public class AuctionController {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final int FIRST_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 16;

    private final AuctionDetailService auctionDetailService;
    private final AuctionCreateService auctionCreateService;
    private final AuctionListService auctionListService;

    @Operation(summary = "경매 상세 조회", description = "경매 방식에 맞는 가격 정보와 판매자·이미지 정보를 조회합니다.")
    @GetMapping("/{auctionId}")
    public ResponseEntity<ApiResponse<AuctionDetailResponse>> getDetail(
            @Parameter(description = "경매 ID", example = "1")
            @PathVariable long auctionId
    ) {
        AuctionDetailResponse response = auctionDetailService.getDetail(auctionId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "경매 등록",
            description = "임시 업로드 이미지와 경매 조건을 검증한 뒤 경매를 등록합니다.",
            security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SECURITY_SCHEME)
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "경매 등록 완료",
            useReturnTypeSchema = true
    )
    @PostMapping
    public ResponseEntity<ApiResponse<AuctionCreateResponse>> create(
            @Login AuthMember authMember,
            @Valid @RequestBody AuctionCreateRequest request
    ) {
        AuctionCreateResponse response = auctionCreateService.create(authMember.memberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @Operation(
            summary = "경매 목록 조회",
            description = "경매 방식·검색어로 필터링한 목록을 조회합니다. `recommended` 외 정렬은 응답의 `asOf`를 다음 페이지 요청에 전달하면 같은 기준 시각으로 조회할 수 있습니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<AuctionListResponse>> getList(
            @Parameter(description = "경매 방식", example = "UP", schema = @Schema(allowableValues = {"UP", "DOWN"}))
            @RequestParam(required = false) String auctionType,
            @Parameter(
                    description = "정렬 기준. 기본값은 recommended",
                    example = "recommended",
                    schema = @Schema(allowableValues = {"recommended", "deadline", "latest", "priceLow", "priceHigh"})
            )
            @RequestParam(required = false) String sort,
            @Parameter(description = "제목 검색어", example = "의자")
            @RequestParam(required = false) String keyword,
            @Parameter(hidden = true)
            @RequestParam(required = false) String status,
            @Parameter(hidden = true)
            @RequestParam(required = false) List<String> category,
            @Parameter(description = "페이지 번호. 1부터 시작", example = "1")
            @RequestParam(required = false, defaultValue = "" + FIRST_PAGE) int page,
            @Parameter(description = "페이지 크기. 최대 100", example = "16")
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            @Parameter(description = "목록 계산 기준 시각(epoch milliseconds). `recommended` 외 정렬에서 첫 응답의 asOf를 재사용", example = "1786860000000")
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
