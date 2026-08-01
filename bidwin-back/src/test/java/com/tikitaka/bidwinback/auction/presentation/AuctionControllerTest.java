package com.tikitaka.bidwinback.auction.presentation;

import com.tikitaka.bidwinback.auction.application.AuctionDetailService;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.enums.TradeType;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionSellerResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.DownAuctionDetailResponse;
import com.tikitaka.bidwinback.auction.presentation.dto.response.UpAuctionDetailResponse;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuctionControllerTest {

    @Mock
    private AuctionDetailService auctionDetailService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuctionController(auctionDetailService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 상승_경매_상세를_조회하면_200과_유형별_정보를_응답한다() throws Exception {
        when(auctionDetailService.getDetail(1L)).thenReturn(upAuctionResponse());

        mockMvc.perform(get("/api/v1/auctions/{auctionId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.auctionId").value(1L))
                .andExpect(jsonPath("$.data.auctionType").value("UP"))
                .andExpect(jsonPath("$.data.currentPrice").value(240_000L))
                .andExpect(jsonPath("$.data.buyNowPrice").value(300_000L))
                .andExpect(jsonPath("$.data.bidCount").value(3L))
                .andExpect(jsonPath("$.data.images[0]")
                        .value("https://cdn.example.com/product.jpg"))
                .andExpect(jsonPath("$.data.seller.name").value("판매자"))
                .andExpect(jsonPath("$.data.seller.verified").value(true))
                .andExpect(jsonPath("$.data.seller.dealCount").value(12L))
                .andExpect(jsonPath("$.data.startedAt").doesNotExist());

        verify(auctionDetailService).getDetail(1L);
    }

    @Test
    void 하락_경매_상세를_조회하면_200과_가격_하락_정보를_응답한다() throws Exception {
        when(auctionDetailService.getDetail(2L)).thenReturn(downAuctionResponse());

        mockMvc.perform(get("/api/v1/auctions/{auctionId}", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.auctionType").value("DOWN"))
                .andExpect(jsonPath("$.data.minimumPrice").value(150_000L))
                .andExpect(jsonPath("$.data.dropPrice").value(10_000L))
                .andExpect(jsonPath("$.data.priceDropIntervalMs").value(600_000L))
                .andExpect(jsonPath("$.data.serverTime").value(1_754_020_500_000L))
                .andExpect(jsonPath("$.data.currentPrice").doesNotExist());

        verify(auctionDetailService).getDetail(2L);
    }

    @Test
    void 존재하지_않는_경매를_조회하면_404를_응답한다() throws Exception {
        when(auctionDetailService.getDetail(999L))
                .thenThrow(new AuctionException(ErrorCode.AUCTION_NOT_FOUND));

        mockMvc.perform(get("/api/v1/auctions/{auctionId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUCTION_404_1"));
    }

    private UpAuctionDetailResponse upAuctionResponse() {
        return new UpAuctionDetailResponse(
                1L,
                AuctionType.UP,
                "헤드폰",
                "미개봉 상품",
                AuctionCategory.HOUSEHOLD,
                AuctionStatus.BID_ONGOING,
                List.of("https://cdn.example.com/product.jpg"),
                200_000L,
                1_754_022_000_000L,
                TradeType.DELIVERY,
                "01012345678",
                new AuctionSellerResponse(
                        10L,
                        "판매자",
                        "https://cdn.example.com/profile.jpg",
                        true,
                        12L
                ),
                300_000L,
                240_000L,
                3L
        );
    }

    private DownAuctionDetailResponse downAuctionResponse() {
        return new DownAuctionDetailResponse(
                2L,
                AuctionType.DOWN,
                "냉장고",
                "이사 정리",
                AuctionCategory.HOUSEHOLD,
                AuctionStatus.BID_ONGOING,
                List.of(),
                200_000L,
                1_754_018_400_000L,
                1_754_020_500_000L,
                1_754_022_000_000L,
                TradeType.DIRECT,
                "01012345678",
                new AuctionSellerResponse(
                        10L,
                        "판매자",
                        "https://cdn.example.com/profile.jpg",
                        true,
                        12L
                ),
                150_000L,
                10_000L,
                600_000L
        );
    }
}
