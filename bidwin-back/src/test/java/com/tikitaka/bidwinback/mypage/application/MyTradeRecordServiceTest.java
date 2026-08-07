package com.tikitaka.bidwinback.mypage.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionTradeRepository;
import com.tikitaka.bidwinback.auction.domain.repository.ImageRepository;
import com.tikitaka.bidwinback.global.common.PageResponse;
import com.tikitaka.bidwinback.global.storage.ImageUrlResolver;
import com.tikitaka.bidwinback.mypage.domain.TradeRoute;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.MyTradeRecordResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyTradeRecordServiceTest {

    private static final long MEMBER_ID = 1L;
    private static final LocalDateTime PURCHASED_AT = LocalDateTime.of(2026, 8, 1, 12, 0);

    @Mock
    private AuctionTradeRepository auctionTradeRepository;
    @Mock
    private ImageRepository imageRepository;
    @Mock
    private ImageUrlResolver imageUrlResolver;

    private MyTradeRecordService service;

    @BeforeEach
    void setUp() {
        service = new MyTradeRecordService(auctionTradeRepository, imageRepository, imageUrlResolver);
        lenient().when(imageRepository.findFirstImageByAuctionIds(anyList())).thenReturn(List.of());
    }

    @Test
    void 상향_경매_낙찰은_route가_WON이다() {
        UpAuction auction = mock(UpAuction.class);
        when(auction.getId()).thenReturn(1L);
        when(auction.getTitle()).thenReturn("헤드폰");
        stubTradePage(trade(auction, TradeStatus.COMPLETED, 300_000L));

        PageResponse<MyTradeRecordResponse> response =
                service.getTrades(MEMBER_ID, null, 1, 10, null);

        assertThat(response.items().getFirst().route()).isEqualTo(TradeRoute.WON);
    }

    @Test
    void 하향_경매_구매는_route가_BUY_NOW이다() {
        DownAuction auction = mock(DownAuction.class);
        when(auction.getId()).thenReturn(2L);
        when(auction.getTitle()).thenReturn("책상");
        stubTradePage(trade(auction, TradeStatus.COMPLETED, 50_000L));

        PageResponse<MyTradeRecordResponse> response =
                service.getTrades(MEMBER_ID, null, 1, 10, null);

        assertThat(response.items().getFirst().route()).isEqualTo(TradeRoute.BUY_NOW);
    }

    private AuctionTrade trade(Auction auction, TradeStatus status, long finalPrice) {
        AuctionTrade trade = mock(AuctionTrade.class);
        when(trade.getAuction()).thenReturn(auction);
        when(trade.getStatus()).thenReturn(status);
        when(trade.getFinalPrice()).thenReturn(finalPrice);
        when(trade.getPurchasedAt()).thenReturn(PURCHASED_AT);
        return trade;
    }

    private void stubTradePage(AuctionTrade trade) {
        Page<AuctionTrade> page = new PageImpl<>(List.of(trade), PageRequest.of(0, 10), 1);
        when(auctionTradeRepository.findByBuyerIdAndStatusIn(eq(MEMBER_ID), any(), any()))
                .thenReturn(page);
    }
}
