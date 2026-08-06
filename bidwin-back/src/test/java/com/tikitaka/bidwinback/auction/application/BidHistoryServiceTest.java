package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.BidRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.BidHistoryRow;
import com.tikitaka.bidwinback.auction.presentation.dto.response.BidHistoryResponse;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidHistoryServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    private BidHistoryService bidHistoryService;

    @BeforeEach
    void setUp() {
        bidHistoryService = new BidHistoryService(auctionRepository, bidRepository);
    }

    @Test
    void 최신_입찰순으로_본인_여부와_마스킹된_닉네임을_응답한다() {
        UpAuction auction = mock(UpAuction.class);
        LocalDateTime latestBidAt = LocalDateTime.of(2026, 8, 2, 18, 2);
        LocalDateTime previousBidAt = LocalDateTime.of(2026, 8, 2, 18, 1);
        when(auctionRepository.findById(1L)).thenReturn(Optional.of(auction));
        when(bidRepository.countVisibleByAuctionId(1L, BidStatus.SEALED, false))
                .thenReturn(15L);
        when(bidRepository.findVisibleHistoryByAuctionId(1L, BidStatus.SEALED, false))
                .thenReturn(List.of(
                new BidHistoryRow(13L, 7L, "내닉네임", 210_000L, latestBidAt),
                new BidHistoryRow(12L, 8L, "민준마켓", 200_000L, previousBidAt),
                new BidHistoryRow(11L, 9L, "김", 190_000L, previousBidAt),
                new BidHistoryRow(10L, 10L, "김희", 180_000L, previousBidAt)
        ));

        BidHistoryResponse response = bidHistoryService.getBidHistory(1L, 7L);

        assertThat(response.bidCount()).isEqualTo(15L);
        assertThat(response.bidLog()).satisfiesExactly(
                bid -> {
                    assertThat(bid.id()).isEqualTo(13L);
                    assertThat(bid.bidder()).isEqualTo("나");
                    assertThat(bid.amount()).isEqualTo(210_000L);
                    assertThat(bid.biddedAt()).isEqualTo(toEpochMilli(latestBidAt));
                    assertThat(bid.isMe()).isTrue();
                },
                bid -> {
                    assertThat(bid.id()).isEqualTo(12L);
                    assertThat(bid.bidder()).isEqualTo("민**켓");
                    assertThat(bid.isMe()).isFalse();
                },
                bid -> assertThat(bid.bidder()).isEqualTo("*"),
                bid -> assertThat(bid.bidder()).isEqualTo("김*")
        );
        verify(bidRepository).countVisibleByAuctionId(1L, BidStatus.SEALED, false);
        verify(bidRepository).findVisibleHistoryByAuctionId(1L, BidStatus.SEALED, false);
    }

    @Test
    void 입찰이_없으면_빈_목록과_0건을_응답한다() {
        when(auctionRepository.findById(1L)).thenReturn(Optional.of(mock(UpAuction.class)));
        when(bidRepository.countVisibleByAuctionId(1L, BidStatus.SEALED, false))
                .thenReturn(0L);
        when(bidRepository.findVisibleHistoryByAuctionId(1L, BidStatus.SEALED, false))
                .thenReturn(List.of());

        BidHistoryResponse response = bidHistoryService.getBidHistory(1L, 7L);

        assertThat(response.bidCount()).isZero();
        assertThat(response.bidLog()).isEmpty();
    }

    @Test
    void 종료_상태에서는_SEALED_입찰을_공개한다() {
        UpAuction auction = mock(UpAuction.class);
        LocalDateTime sealedBidAt = LocalDateTime.of(2026, 8, 2, 18, 5);
        when(auction.isSealedBidRevealed()).thenReturn(true);
        when(auctionRepository.findById(1L)).thenReturn(Optional.of(auction));
        when(bidRepository.countVisibleByAuctionId(1L, BidStatus.SEALED, true))
                .thenReturn(2L);
        when(bidRepository.findVisibleHistoryByAuctionId(1L, BidStatus.SEALED, true))
                .thenReturn(List.of(
                        new BidHistoryRow(
                                14L,
                                7L,
                                "내닉네임",
                                250_000L,
                                sealedBidAt
                        )
                ));

        BidHistoryResponse response = bidHistoryService.getBidHistory(1L, 7L);

        assertThat(response.bidCount()).isEqualTo(2L);
        assertThat(response.bidLog()).singleElement().satisfies(bid -> {
            assertThat(bid.amount()).isEqualTo(250_000L);
            assertThat(bid.isMe()).isTrue();
        });
        verify(bidRepository).countVisibleByAuctionId(1L, BidStatus.SEALED, true);
        verify(bidRepository).findVisibleHistoryByAuctionId(1L, BidStatus.SEALED, true);
    }

    @Test
    void 존재하지_않는_경매는_404_예외가_발생한다() {
        when(auctionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatExceptionOfType(AuctionException.class)
                .isThrownBy(() -> bidHistoryService.getBidHistory(999L, 7L))
                .extracting(AuctionException::getErrorCode)
                .isEqualTo(ErrorCode.AUCTION_NOT_FOUND);

        verify(bidRepository, never())
                .findVisibleHistoryByAuctionId(999L, BidStatus.SEALED, false);
        verify(bidRepository, never())
                .countVisibleByAuctionId(999L, BidStatus.SEALED, false);
    }

    @Test
    void 하락_경매도_구매_기록을_입찰_내역으로_조회한다() {
        when(auctionRepository.findById(2L)).thenReturn(Optional.of(mock(DownAuction.class)));
        when(bidRepository.countVisibleByAuctionId(2L, BidStatus.SEALED, false))
                .thenReturn(1L);
        when(bidRepository.findVisibleHistoryByAuctionId(2L, BidStatus.SEALED, false))
                .thenReturn(List.of(
                new BidHistoryRow(
                        14L,
                        8L,
                        "하향구매자",
                        180_000L,
                        LocalDateTime.of(2026, 8, 2, 18, 3)
                )
        ));

        BidHistoryResponse response = bidHistoryService.getBidHistory(2L, 7L);

        assertThat(response.bidCount()).isEqualTo(1L);
        assertThat(response.bidLog().getFirst().bidder()).isEqualTo("하***자");
        assertThat(response.bidLog().getFirst().amount()).isEqualTo(180_000L);
        verify(bidRepository).findVisibleHistoryByAuctionId(2L, BidStatus.SEALED, false);
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(SEOUL).toInstant().toEpochMilli();
    }
}
