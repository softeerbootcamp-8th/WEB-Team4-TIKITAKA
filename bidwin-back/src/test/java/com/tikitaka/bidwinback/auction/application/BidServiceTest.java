package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Bid;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.BidStatus;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.domain.exception.BidException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.BidRepository;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_NOT_FOUND;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.BID_PRICE_TOO_LOW;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_BID_UNIT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.NOT_UP_AUCTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long AUCTION_ID = 42L;
    private static final Long BID_ID = 7L;
    private static final long CURRENT_PRICE = 231_000L;
    private static final long PRICE = 232_000L;
    private static final LocalDateTime BID_AT =
            LocalDateTime.of(2026, 7, 30, 12, 34, 56);

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private Member bidder;

    @Mock
    private UpAuction auction;

    @Mock
    private DownAuction downAuction;

    @Mock
    private Bid persistedBid;

    @InjectMocks
    private BidService bidService;

    @Test
    void 입찰하면_UP_상태의_입찰_기록을_한_건_저장한다() {
        // given
        stubLoadedEntities();
        stubPersistedBid();
        when(bidRepository.save(any(Bid.class))).thenReturn(persistedBid);

        // when
        BidResult result = bidService.place(MEMBER_ID, AUCTION_ID, PRICE);

        // then
        ArgumentCaptor<Bid> bidCaptor = ArgumentCaptor.forClass(Bid.class);
        verify(bidRepository).save(bidCaptor.capture());
        Bid saved = bidCaptor.getValue();
        assertAll(
                () -> assertThat(saved.getAuction()).isSameAs(auction),
                () -> assertThat(saved.getBidder()).isSameAs(bidder),
                () -> assertThat(saved.getPrice()).isEqualTo(PRICE),
                () -> assertThat(saved.getStatus()).isEqualTo(BidStatus.UP),
                () -> assertThat(result.bidId()).isEqualTo(BID_ID),
                () -> assertThat(result.auctionId()).isEqualTo(AUCTION_ID),
                () -> assertThat(result.bidderId()).isEqualTo(MEMBER_ID),
                () -> assertThat(result.price()).isEqualTo(PRICE),
                () -> assertThat(result.status()).isEqualTo(BidStatus.UP),
                () -> assertThat(result.bidAt()).isEqualTo(BID_AT)
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1_000L, 232_500L})
    void 양수가_아니거나_천원_단위가_아닌_가격은_Repository_호출_없이_거절한다(
            long invalidPrice
    ) {
        BidException exception = assertThrows(
                BidException.class,
                () -> bidService.place(MEMBER_ID, AUCTION_ID, invalidPrice)
        );

        assertThat(exception.getErrorCode()).isEqualTo(INVALID_BID_UNIT);
        verifyNoInteractions(memberRepository, auctionRepository, bidRepository);
    }

    @Test
    void 첫_입찰은_시작가보다_천원_높으면_성공한다() {
        when(auctionRepository.findById(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(bidRepository.findHighestPriceByAuctionId(AUCTION_ID)).thenReturn(null);
        when(auction.getStartPrice()).thenReturn(CURRENT_PRICE);
        when(memberRepository.getReferenceById(MEMBER_ID)).thenReturn(bidder);
        stubPersistedBid();
        when(bidRepository.save(any(Bid.class))).thenReturn(persistedBid);

        BidResult result = bidService.place(MEMBER_ID, AUCTION_ID, PRICE);

        assertThat(result.price()).isEqualTo(PRICE);
        verify(bidRepository).save(any(Bid.class));
    }

    @Test
    void 첫_입찰이_시작가와_같으면_거절한다() {
        when(auctionRepository.findById(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(bidRepository.findHighestPriceByAuctionId(AUCTION_ID)).thenReturn(null);
        when(auction.getStartPrice()).thenReturn(CURRENT_PRICE);

        BidException exception = assertThrows(
                BidException.class,
                () -> bidService.place(MEMBER_ID, AUCTION_ID, CURRENT_PRICE)
        );

        assertThat(exception.getErrorCode()).isEqualTo(BID_PRICE_TOO_LOW);
        verifyNoInteractions(memberRepository);
        verify(bidRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(longs = {230_000L, 231_000L, 232_000L})
    void 현재가_이하이거나_증가액이_천원보다_작으면_입찰할_수_없다(long price) {
        when(auctionRepository.findById(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(bidRepository.findHighestPriceByAuctionId(AUCTION_ID))
                .thenReturn(price == 232_000L ? 231_500L : CURRENT_PRICE);

        BidException exception = assertThrows(
                BidException.class,
                () -> bidService.place(MEMBER_ID, AUCTION_ID, price)
        );

        assertThat(exception.getErrorCode()).isEqualTo(BID_PRICE_TOO_LOW);
        verifyNoInteractions(memberRepository);
        verify(bidRepository, never()).save(any());
    }

    @Test
    void 경매_조회_후_최고가를_확인하고_회원을_참조해_저장한다() {
        stubLoadedEntities();
        stubPersistedBid();
        when(bidRepository.save(any(Bid.class))).thenReturn(persistedBid);

        bidService.place(MEMBER_ID, AUCTION_ID, PRICE);

        InOrder order = inOrder(auctionRepository, bidRepository, memberRepository);
        order.verify(auctionRepository).findById(AUCTION_ID);
        order.verify(bidRepository).findHighestPriceByAuctionId(AUCTION_ID);
        order.verify(memberRepository).getReferenceById(MEMBER_ID);
        order.verify(bidRepository).save(any(Bid.class));
    }

    @Test
    void 인증된_회원은_일반_조회하지_않고_JPA_참조로_연결한다() {
        // given
        stubLoadedEntities();
        stubPersistedBid();
        when(bidRepository.save(any(Bid.class))).thenReturn(persistedBid);

        // when
        bidService.place(MEMBER_ID, AUCTION_ID, PRICE);

        // then
        verify(memberRepository).getReferenceById(MEMBER_ID);
        verify(memberRepository, never()).findById(any());
    }

    @Test
    void 존재하지_않는_경매에는_입찰할_수_없다() {
        // given
        when(auctionRepository.findById(AUCTION_ID)).thenReturn(Optional.empty());

        // when
        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> bidService.place(MEMBER_ID, AUCTION_ID, PRICE)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(AUCTION_NOT_FOUND);
        verifyNoInteractions(memberRepository, bidRepository);
    }

    @Test
    void 하향_경매에는_입찰할_수_없다() {
        // given
        when(auctionRepository.findById(AUCTION_ID))
                .thenReturn(Optional.of(downAuction));

        // when
        BidException exception = assertThrows(
                BidException.class,
                () -> bidService.place(MEMBER_ID, AUCTION_ID, PRICE)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(NOT_UP_AUCTION);
        verifyNoInteractions(memberRepository, bidRepository);
    }

    private void stubLoadedEntities() {
        when(auctionRepository.findById(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(bidRepository.findHighestPriceByAuctionId(AUCTION_ID))
                .thenReturn(CURRENT_PRICE);
        when(memberRepository.getReferenceById(MEMBER_ID)).thenReturn(bidder);
    }

    private void stubPersistedBid() {
        when(persistedBid.getId()).thenReturn(BID_ID);
        when(persistedBid.getAuction()).thenReturn(auction);
        when(auction.getId()).thenReturn(AUCTION_ID);
        when(persistedBid.getBidder()).thenReturn(bidder);
        when(bidder.getId()).thenReturn(MEMBER_ID);
        when(persistedBid.getPrice()).thenReturn(PRICE);
        when(persistedBid.getStatus()).thenReturn(BidStatus.UP);
        when(persistedBid.getCreatedAt()).thenReturn(BID_AT);
    }
}
