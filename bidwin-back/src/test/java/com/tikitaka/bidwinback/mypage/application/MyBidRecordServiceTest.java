package com.tikitaka.bidwinback.mypage.application;

import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.BidRepository;
import com.tikitaka.bidwinback.auction.domain.repository.ImageRepository;
import com.tikitaka.bidwinback.auction.domain.repository.SealedBidRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionBidSummary;
import com.tikitaka.bidwinback.auction.domain.repository.dto.MyBidAggregate;
import com.tikitaka.bidwinback.global.common.PageResponse;
import com.tikitaka.bidwinback.global.storage.ImageUrlResolver;
import com.tikitaka.bidwinback.mypage.presentation.dto.response.MyBidRecordResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyBidRecordServiceTest {

    private static final long MEMBER_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 12, 0);

    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private SealedBidRepository sealedBidRepository;
    @Mock
    private ImageRepository imageRepository;
    @Mock
    private ImageUrlResolver imageUrlResolver;

    private MyBidRecordService service;

    @BeforeEach
    void setUp() {
        service = new MyBidRecordService(
                auctionRepository,
                bidRepository,
                sealedBidRepository,
                imageRepository,
                imageUrlResolver
        );
        lenient().when(auctionRepository.currentDatabaseTime()).thenReturn(NOW);
        lenient().when(imageRepository.findFirstImageByAuctionIds(anyList())).thenReturn(List.of());
    }

    @Test
    void 내_최고가가_전체_최고가와_같으면_최고가_입찰중이다() {
        stubMyBids(List.of(new MyBidAggregate(1L, 200_000L, NOW.minusMinutes(5))));
        stubOverallHighest(List.of(new AuctionBidSummary(1L, 200_000L, 3L)));
        UpAuction auction = activeAuction(1L, NOW.plusHours(1));
        when(auctionRepository.findAllById(anyCollection())).thenReturn(List.of(auction));

        PageResponse<MyBidRecordResponse> response = service.getBids(MEMBER_ID, null, 1, 10, null);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().isWinning()).isTrue();
        assertThat(response.items().getFirst().myBidAmount()).isEqualTo(200_000L);
    }

    @Test
    void 다른_사람이_더_높으면_최고가_입찰중이_아니다() {
        stubMyBids(List.of(new MyBidAggregate(1L, 200_000L, NOW.minusMinutes(5))));
        stubOverallHighest(List.of(new AuctionBidSummary(1L, 250_000L, 4L)));
        UpAuction auction = activeAuction(1L, NOW.plusHours(1));
        when(auctionRepository.findAllById(anyCollection())).thenReturn(List.of(auction));

        PageResponse<MyBidRecordResponse> response = service.getBids(MEMBER_ID, null, 1, 10, null);

        assertThat(response.items().getFirst().isWinning()).isFalse();
    }

    @Test
    void 밀봉입찰만_넣은_경매도_후보에_포함되고_밀봉구간으로_표시된다() {
        // 공개 입찰은 없고 밀봉 입찰만 있는 경우 — Bid 쪽 집계는 빈 목록, SealedBid 쪽만 값이 있음.
        when(bidRepository.summarizeMyBidsByMemberId(MEMBER_ID)).thenReturn(List.of());
        when(sealedBidRepository.summarizeMySealedBidsByMemberId(MEMBER_ID))
                .thenReturn(List.of(new MyBidAggregate(1L, 300_000L, NOW.minusMinutes(1))));
        when(bidRepository.summarizeByAuctionIds(anyList(), eq(NOW))).thenReturn(List.of());
        when(sealedBidRepository.summarizeSealedByAuctionIds(anyList()))
                .thenReturn(List.of(new AuctionBidSummary(1L, 300_000L, 1L)));

        // 마감 5분 이내(밀봉 구간)인 경매
        UpAuction auction = activeAuction(1L, NOW.plusMinutes(2));
        when(auctionRepository.findAllById(anyCollection())).thenReturn(List.of(auction));

        PageResponse<MyBidRecordResponse> response = service.getBids(MEMBER_ID, null, 1, 10, null);

        assertThat(response.items()).hasSize(1);
        MyBidRecordResponse record = response.items().getFirst();
        assertThat(record.isSealedPhase()).isTrue();
        assertThat(record.isWinning()).isTrue();
        assertThat(record.myBidAmount()).isEqualTo(300_000L);
    }

    @Test
    void 이미_종료된_경매는_후보에서_제외된다() {
        stubMyBids(List.of(new MyBidAggregate(1L, 200_000L, NOW.minusMinutes(5))));
        UpAuction endedAuction = mock(UpAuction.class);
        when(endedAuction.getStatus()).thenReturn(AuctionStatus.OPEN);
        when(endedAuction.getEndedAt()).thenReturn(NOW.minusMinutes(1));
        when(auctionRepository.findAllById(anyCollection())).thenReturn(List.of(endedAuction));

        PageResponse<MyBidRecordResponse> response = service.getBids(MEMBER_ID, null, 1, 10, null);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalCount()).isZero();
    }

    @Test
    void 입찰한_경매가_없으면_빈_페이지를_반환한다() {
        when(bidRepository.summarizeMyBidsByMemberId(MEMBER_ID)).thenReturn(List.of());
        when(sealedBidRepository.summarizeMySealedBidsByMemberId(MEMBER_ID)).thenReturn(List.of());

        PageResponse<MyBidRecordResponse> response = service.getBids(MEMBER_ID, null, 1, 10, null);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalCount()).isZero();
    }

    @Test
    void WINNING_필터는_최고가_입찰중인_것만_남긴다() {
        stubMyBids(List.of(
                new MyBidAggregate(1L, 200_000L, NOW.minusMinutes(5)),
                new MyBidAggregate(2L, 100_000L, NOW.minusMinutes(3))
        ));
        stubOverallHighest(List.of(
                new AuctionBidSummary(1L, 200_000L, 3L),
                new AuctionBidSummary(2L, 150_000L, 5L)
        ));
        UpAuction winning = activeAuction(1L, NOW.plusHours(1));
        UpAuction losing = activeAuction(2L, NOW.plusHours(1));
        when(auctionRepository.findAllById(anyCollection())).thenReturn(List.of(winning, losing));

        PageResponse<MyBidRecordResponse> response = service.getBids(MEMBER_ID, "WINNING", 1, 10, null);

        assertThat(response.items()).extracting(MyBidRecordResponse::auctionId).containsExactly(1L);
    }

    private void stubMyBids(List<MyBidAggregate> aggregates) {
        when(bidRepository.summarizeMyBidsByMemberId(MEMBER_ID)).thenReturn(aggregates);
        lenient().when(sealedBidRepository.summarizeMySealedBidsByMemberId(MEMBER_ID)).thenReturn(List.of());
    }

    private void stubOverallHighest(List<AuctionBidSummary> summaries) {
        when(bidRepository.summarizeByAuctionIds(anyList(), eq(NOW))).thenReturn(summaries);
        lenient().when(sealedBidRepository.summarizeSealedByAuctionIds(anyList())).thenReturn(List.of());
    }

    private UpAuction activeAuction(long id, LocalDateTime endedAt) {
        UpAuction auction = mock(UpAuction.class);
        when(auction.getId()).thenReturn(id);
        when(auction.getTitle()).thenReturn("상품" + id);
        when(auction.getStatus()).thenReturn(AuctionStatus.BID_ONGOING);
        when(auction.getEndedAt()).thenReturn(endedAt);
        return auction;
    }

}
