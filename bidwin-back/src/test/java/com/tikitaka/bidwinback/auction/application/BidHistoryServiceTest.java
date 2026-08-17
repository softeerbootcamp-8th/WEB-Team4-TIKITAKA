package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.BidRepository;
import com.tikitaka.bidwinback.auction.domain.repository.SealedBidRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidHistoryServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private SealedBidRepository sealedBidRepository;

    private BidHistoryService bidHistoryService;

    @BeforeEach
    void setUp() {
        bidHistoryService = new BidHistoryService(
                auctionRepository,
                bidRepository,
                sealedBidRepository
        );
    }

    @Test
    void 최신_입찰순으로_마스킹된_닉네임을_응답한다() {
        UpAuction auction = mock(UpAuction.class);
        LocalDateTime latestBidAt = LocalDateTime.of(2026, 8, 2, 18, 2);
        LocalDateTime previousBidAt = LocalDateTime.of(2026, 8, 2, 18, 1);
        when(auctionRepository.findById(1L)).thenReturn(Optional.of(auction));
        when(auction.getStatus()).thenReturn(AuctionStatus.BID_ONGOING);
        when(auction.getBidCount()).thenReturn(15L);
        when(bidRepository.findHistoryByAuctionId(1L)).thenReturn(List.of(
                new BidHistoryRow(13L, 7L, "내닉네임", 210_000L, latestBidAt),
                new BidHistoryRow(12L, 8L, "민준마켓", 200_000L, previousBidAt),
                new BidHistoryRow(11L, 9L, "김", 190_000L, previousBidAt),
                new BidHistoryRow(10L, 10L, "김희", 180_000L, previousBidAt)
        ));

        BidHistoryResponse response = bidHistoryService.getBidHistory(1L);

        assertThat(response.bidCount()).isEqualTo(15L);
        assertThat(response.bidLog()).satisfiesExactly(
                bid -> {
                    assertThat(bid.entryId()).isEqualTo("BID:13");
                    assertThat(bid.bidder()).isEqualTo("내**임");
                    assertThat(bid.amount()).isEqualTo(210_000L);
                    assertThat(bid.biddedAt()).isEqualTo(toEpochMilli(latestBidAt));
                },
                bid -> {
                    assertThat(bid.entryId()).isEqualTo("BID:12");
                    assertThat(bid.bidder()).isEqualTo("민**켓");
                },
                bid -> assertThat(bid.bidder()).isEqualTo("*"),
                bid -> assertThat(bid.bidder()).isEqualTo("김*")
        );
        verify(bidRepository).findHistoryByAuctionId(1L);
    }

    @Test
    void 입찰이_없으면_빈_목록과_0건을_응답한다() {
        UpAuction auction = mock(UpAuction.class);
        when(auctionRepository.findById(1L)).thenReturn(Optional.of(auction));
        when(auction.getStatus()).thenReturn(AuctionStatus.OPEN);
        when(bidRepository.findHistoryByAuctionId(1L)).thenReturn(List.of());

        BidHistoryResponse response = bidHistoryService.getBidHistory(1L);

        assertThat(response.bidCount()).isZero();
        assertThat(response.bidLog()).isEmpty();
    }

    @Test
    void 낙찰자_결정_상태부터_일반입찰과_밀봉입찰을_최신순으로_공개한다() {
        UpAuction auction = mock(UpAuction.class);
        LocalDateTime openBidAt = LocalDateTime.of(2026, 8, 2, 18, 1);
        LocalDateTime latestSealedBidAt = LocalDateTime.of(2026, 8, 2, 18, 2);
        LocalDateTime oldestSealedBidAt = LocalDateTime.of(2026, 8, 2, 18, 0);
        when(auctionRepository.findById(1L)).thenReturn(Optional.of(auction));
        when(auction.getStatus()).thenReturn(AuctionStatus.WINNER_DETERMINING);
        when(auction.getBidCount()).thenReturn(1L);
        when(auction.getSealedBidCount()).thenReturn(2L);
        when(bidRepository.findHistoryByAuctionId(1L)).thenReturn(List.of(
                new BidHistoryRow(13L, 8L, "일반입찰자", 210_000L, openBidAt)
        ));
        when(sealedBidRepository.findHistoryByAuctionId(1L)).thenReturn(List.of(
                new BidHistoryRow(14L, 7L, "내닉네임", 250_000L, latestSealedBidAt),
                new BidHistoryRow(12L, 9L, "밀봉입찰자", 230_000L, oldestSealedBidAt)
        ));

        BidHistoryResponse response = bidHistoryService.getBidHistory(1L);

        assertThat(response.bidCount()).isEqualTo(3L);
        assertThat(response.bidLog()).satisfiesExactly(
                bid -> {
                    assertThat(bid.entryId()).isEqualTo("SEALED:14");
                    assertThat(bid.bidder()).isEqualTo("내**임");
                    assertThat(bid.amount()).isEqualTo(250_000L);
                },
                bid -> {
                    assertThat(bid.entryId()).isEqualTo("BID:13");
                    assertThat(bid.amount()).isEqualTo(210_000L);
                },
                bid -> {
                    assertThat(bid.entryId()).isEqualTo("SEALED:12");
                    assertThat(bid.amount()).isEqualTo(230_000L);
                }
        );
    }

    @Test
    void 존재하지_않는_경매는_404_예외가_발생한다() {
        when(auctionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatExceptionOfType(AuctionException.class)
                .isThrownBy(() -> bidHistoryService.getBidHistory(999L))
                .extracting(AuctionException::getErrorCode)
                .isEqualTo(ErrorCode.AUCTION_NOT_FOUND);

        verify(bidRepository, never()).findHistoryByAuctionId(999L);
        verify(sealedBidRepository, never()).findHistoryByAuctionId(999L);
    }

    @Test
    void 하락_경매도_구매_기록을_입찰_내역으로_조회한다() {
        DownAuction auction = mock(DownAuction.class);
        when(auctionRepository.findById(2L)).thenReturn(Optional.of(auction));
        when(auction.getStatus()).thenReturn(AuctionStatus.COMPLETED);
        when(auction.getBidCount()).thenReturn(1L);
        when(bidRepository.findHistoryByAuctionId(2L)).thenReturn(List.of(
                new BidHistoryRow(
                        14L,
                        8L,
                        "하향구매자",
                        180_000L,
                        LocalDateTime.of(2026, 8, 2, 18, 3)
                )
        ));

        BidHistoryResponse response = bidHistoryService.getBidHistory(2L);

        assertThat(response.bidCount()).isEqualTo(1L);
        assertThat(response.bidLog().getFirst().bidder()).isEqualTo("하***자");
        assertThat(response.bidLog().getFirst().amount()).isEqualTo(180_000L);
        verify(bidRepository).findHistoryByAuctionId(2L);
    }

    @Test
    void 일반입찰과_밀봉입찰의_숫자_ID가_같아도_entryId는_충돌하지_않는다() {
        UpAuction auction = mock(UpAuction.class);
        LocalDateTime bidAt = LocalDateTime.of(2026, 8, 2, 18, 1);
        when(auctionRepository.findById(1L)).thenReturn(Optional.of(auction));
        when(auction.getStatus()).thenReturn(AuctionStatus.COMPLETED);
        when(auction.getBidCount()).thenReturn(1L);
        when(auction.getSealedBidCount()).thenReturn(1L);
        when(bidRepository.findHistoryByAuctionId(1L)).thenReturn(List.of(
                new BidHistoryRow(7L, 8L, "일반입찰자", 210_000L, bidAt)
        ));
        when(sealedBidRepository.findHistoryByAuctionId(1L)).thenReturn(List.of(
                new BidHistoryRow(7L, 9L, "밀봉입찰자", 230_000L, bidAt.plusMinutes(1))
        ));

        BidHistoryResponse response = bidHistoryService.getBidHistory(1L);

        assertThat(response.bidLog())
                .extracting(bid -> bid.entryId())
                .containsExactly("SEALED:7", "BID:7");
    }

    @Test
    void SSE_초기_입찰내역은_검증된_상태와_입찰수를_재사용한다() {
        // given
        when(bidRepository.findHistoryByAuctionId(1L)).thenReturn(List.of());
        when(sealedBidRepository.findHistoryByAuctionId(1L)).thenReturn(List.of());

        // when
        BidHistoryResponse response = bidHistoryService.getBidHistory(
                1L,
                AuctionStatus.COMPLETED,
                17L
        );

        // then
        assertThat(response.bidCount()).isEqualTo(17L);
        verifyNoInteractions(auctionRepository);
        verify(sealedBidRepository).findHistoryByAuctionId(1L);
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(SEOUL).toInstant().toEpochMilli();
    }
}
