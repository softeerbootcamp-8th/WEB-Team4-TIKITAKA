package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.AuctionCategoryService;
import com.tikitaka.bidwinback.auction.presentation.dto.AuctionCategoryResponse;
import com.tikitaka.bidwinback.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class AuctionCategoryController {
    private final AuctionCategoryService auctionCategoryService;
    @GetMapping
    public ResponseEntity<ApiResponse<List<AuctionCategoryResponse>>> getCategories(){
        List<AuctionCategoryResponse> categories = auctionCategoryService.getCategories();
        return ResponseEntity.ok(ApiResponse.success(categories));
    }
}
