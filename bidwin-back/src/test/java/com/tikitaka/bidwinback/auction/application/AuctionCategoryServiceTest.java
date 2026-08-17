package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionCategoryResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionCategoryServiceTest {

    private final AuctionCategoryService auctionCategoryService = new AuctionCategoryService();

    @Test
    void 전체_카테고리의_코드와_라벨을_반환한다() {
        assertThat(auctionCategoryService.getCategories()).containsExactly(
                new AuctionCategoryResponse("HOUSEHOLD", "생활용품"),
                new AuctionCategoryResponse("FOOD", "먹거리"),
                new AuctionCategoryResponse("FURNITURE", "가구"),
                new AuctionCategoryResponse("ELECTRONICS", "디지털/가전"),
                new AuctionCategoryResponse("FASHION", "패션/잡화"),
                new AuctionCategoryResponse("SPORTS", "스포츠/레저"),
                new AuctionCategoryResponse("HOBBY", "취미/수집"),
                new AuctionCategoryResponse("BOOK", "도서/문구"),
                new AuctionCategoryResponse("OTHER", "기타")
        );
    }
}
