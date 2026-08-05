package com.tikitaka.bidwinback.auction.application.live;

import com.tikitaka.bidwinback.auction.application.BuyNowPriceCalculator;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionTradeRepository;
import com.tikitaka.bidwinback.auction.domain.repository.BidRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionLiveStateServiceTest {

    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private AuctionTradeRepository auctionTradeRepository;
    @Mock
    private BuyNowPriceCalculator priceCalculator;
    @Mock
    private UpAuction auction;

    private AuctionLiveStateService stateService;

    @BeforeEach
    void setUp() {
        stateService = new AuctionLiveStateService(
                auctionRepository,
                bidRepository,
                auctionTradeRepository,
                priceCalculator
        );
    }

    @Test
    void 진행중_경매는_revision과_현재가와_입찰수를_절대_상태로_만든다() {
        // given
        LocalDateTime databaseTime = LocalDateTime.of(2026, 8, 4, 12, 0);
        when(auctionRepository.findById(1L)).thenReturn(Optional.of(auction));
        when(auctionRepository.currentDatabaseTime()).thenReturn(databaseTime);
        when(auction.getId()).thenReturn(1L);
        when(auction.getRevision()).thenReturn(7L);
        when(auction.getStatus()).thenReturn(AuctionStatus.BID_ONGOING);
        when(auction.getCurrentPrice()).thenReturn(145_000L);
        when(bidRepository.countByAuctionId(1L)).thenReturn(4L);

        // when
        AuctionLiveState state = stateService.getState(1L);

        // then
        assertThat(state).isEqualTo(new AuctionLiveState(
                1L,
                7L,
                AuctionType.UP,
                AuctionStatus.BID_ONGOING,
                145_000L,
                4L
        ));
    }

    @Test
    void 하향_경매는_클라이언트가_스스로_계산하도록_DOWN_타입으로_알린다() {
        // given
        LocalDateTime databaseTime = LocalDateTime.of(2026, 8, 4, 12, 0);
        DownAuction downAuction = mock(DownAuction.class);
        when(auctionRepository.findById(2L)).thenReturn(Optional.of(downAuction));
        when(auctionRepository.currentDatabaseTime()).thenReturn(databaseTime);
        when(downAuction.getId()).thenReturn(2L);
        when(downAuction.getStatus()).thenReturn(AuctionStatus.OPEN);
        when(downAuction.getEndedAt()).thenReturn(databaseTime.plusHours(1));
        when(priceCalculator.calculate(downAuction, databaseTime)).thenReturn(88_000L);
        when(bidRepository.countByAuctionId(2L)).thenReturn(0L);

        // when
        AuctionLiveState state = stateService.getState(2L);

        // then
        assertThat(state.auctionType()).isEqualTo(AuctionType.DOWN);
        assertThat(state.currentPrice()).isEqualTo(88_000L);
    }

    @Test
    void 마감이_지난_하향_경매는_마감_시각의_가격에서_멈춘다() {
        // given
        LocalDateTime databaseTime = LocalDateTime.of(2026, 8, 4, 12, 0);
        LocalDateTime endedAt = databaseTime.minusMinutes(30);
        DownAuction downAuction = mock(DownAuction.class);
        when(auctionRepository.findById(2L)).thenReturn(Optional.of(downAuction));
        when(auctionRepository.currentDatabaseTime()).thenReturn(databaseTime);
        when(downAuction.getId()).thenReturn(2L);
        when(downAuction.getStatus()).thenReturn(AuctionStatus.OPEN);
        when(downAuction.getEndedAt()).thenReturn(endedAt);
        when(priceCalculator.calculate(downAuction, endedAt)).thenReturn(70_000L);
        when(bidRepository.countByAuctionId(2L)).thenReturn(0L);

        // when
        AuctionLiveState state = stateService.getState(2L);

        // then
        assertThat(state.currentPrice()).isEqualTo(70_000L);
    }

    @Test
    void 현재가는_Bid_집계가_아니라_경매_행에서_읽는다() {
        // given
        when(auctionRepository.findById(1L)).thenReturn(Optional.of(auction));
        when(auctionRepository.currentDatabaseTime()).thenReturn(LocalDateTime.now());
        when(auction.getId()).thenReturn(1L);
        when(auction.getStatus()).thenReturn(AuctionStatus.BID_ONGOING);
        when(auction.getCurrentPrice()).thenReturn(132_000L);
        when(bidRepository.countByAuctionId(1L)).thenReturn(2L);

        // when
        AuctionLiveState state = stateService.getState(1L);

        // then
        assertThat(state.currentPrice()).isEqualTo(132_000L);
        verify(bidRepository, never()).findHighestPriceByAuctionId(anyLong());
    }

    @Test
    void 완료된_경매는_경매_행이_아니라_확정_거래가를_현재가로_보낸다() {
        // given
        when(auctionRepository.findById(1L)).thenReturn(Optional.of(auction));
        when(auctionRepository.currentDatabaseTime()).thenReturn(LocalDateTime.now());
        when(auction.getId()).thenReturn(1L);
        when(auction.getRevision()).thenReturn(8L);
        when(auction.getStatus()).thenReturn(AuctionStatus.COMPLETED);
        when(bidRepository.countByAuctionId(1L)).thenReturn(4L);
        when(auctionTradeRepository.findFinalPriceByAuctionId(1L))
                .thenReturn(Optional.of(180_000L));

        // when
        AuctionLiveState state = stateService.getState(1L);

        // then
        assertThat(state.currentPrice()).isEqualTo(180_000L);
        verify(auction, never()).getCurrentPrice();
    }

    @Test
    void 완료된_경매에_확정_거래가가_없으면_추정값을_SSE로_보내지_않는다() {
        // given
        when(auctionRepository.findById(1L)).thenReturn(Optional.of(auction));
        when(auctionRepository.currentDatabaseTime()).thenReturn(LocalDateTime.now());
        when(auction.getId()).thenReturn(1L);
        when(auction.getStatus()).thenReturn(AuctionStatus.COMPLETED);
        when(auctionTradeRepository.findFinalPriceByAuctionId(1L))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> stateService.getState(1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void heartbeat_기준_시각은_데이터베이스_시각을_epoch_밀리초로_제공한다() {
        // given
        LocalDateTime databaseTime = LocalDateTime.of(2026, 8, 5, 12, 30);
        when(auctionRepository.currentDatabaseTime()).thenReturn(databaseTime);

        // when
        long serverTime = stateService.getDatabaseTimeMillis();

        // then
        assertThat(serverTime).isEqualTo(databaseTime
                .atZone(ZoneId.of("Asia/Seoul"))
                .toInstant()
                .toEpochMilli());
    }
}
