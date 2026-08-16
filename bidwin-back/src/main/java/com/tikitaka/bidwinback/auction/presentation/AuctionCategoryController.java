package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.AuctionCategoryService;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionCategoryResponse;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "카테고리", description = "경매 상품 카테고리 조회")
public class AuctionCategoryController {
    private final AuctionCategoryService auctionCategoryService;

    @Operation(summary = "경매 카테고리 목록 조회", description = "경매 등록과 검색에 사용할 수 있는 전체 카테고리를 반환합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AuctionCategoryResponse>>> getCategories() {
        List<AuctionCategoryResponse> categories = auctionCategoryService.getCategories();
        return ResponseEntity.ok(ApiResponse.success(categories));
    }
}
