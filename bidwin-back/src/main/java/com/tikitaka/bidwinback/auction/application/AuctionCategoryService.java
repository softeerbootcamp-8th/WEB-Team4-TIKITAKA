package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionCategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuctionCategoryService {
    public List<AuctionCategoryResponse> getCategories(){
        return Arrays.stream(AuctionCategory.values())
                .map(AuctionCategoryResponse::from)
                .toList();
    }
}
