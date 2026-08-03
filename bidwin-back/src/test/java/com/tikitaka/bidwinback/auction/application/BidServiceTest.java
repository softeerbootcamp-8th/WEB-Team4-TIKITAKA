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
import com.tikitaka.bidwinback.member.domain.exception.MemberException;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_NOT_FOUND;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.MEMBER_NOT_FOUND;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.NOT_UP_AUCTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long AUCTION_ID = 42L;
    private static final Long BID_ID = 7L;
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

    @Test
    void 존재하지_않는_회원은_입찰할_수_없다() {
        // given
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        // when
        MemberException exception = assertThrows(
                MemberException.class,
                () -> bidService.place(MEMBER_ID, AUCTION_ID, PRICE)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(MEMBER_NOT_FOUND);
        verifyNoInteractions(auctionRepository, bidRepository);
    }

    @Test
    void 존재하지_않는_경매에는_입찰할_수_없다() {
        // given
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(bidder));
        when(auctionRepository.findById(AUCTION_ID)).thenReturn(Optional.empty());

        // when
        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> bidService.place(MEMBER_ID, AUCTION_ID, PRICE)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(AUCTION_NOT_FOUND);
        verify(bidRepository, never()).save(any());
    }

    @Test
    void 하향_경매에는_입찰할_수_없다() {
        // given
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(bidder));
        when(auctionRepository.findById(AUCTION_ID))
                .thenReturn(Optional.of(downAuction));

        // when
        BidException exception = assertThrows(
                BidException.class,
                () -> bidService.place(MEMBER_ID, AUCTION_ID, PRICE)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(NOT_UP_AUCTION);
        verify(bidRepository, never()).save(any());
    }

    private void stubLoadedEntities() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(bidder));
        when(auctionRepository.findById(AUCTION_ID)).thenReturn(Optional.of(auction));
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
