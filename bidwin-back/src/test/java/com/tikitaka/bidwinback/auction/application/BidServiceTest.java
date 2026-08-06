package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.Bid;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
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
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_ALREADY_ENDED;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_NOT_FOUND;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.AUCTION_NOT_ONGOING;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.BID_PHASE_MISMATCH;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.BID_PRICE_TOO_LOW;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.CONCURRENT_BID_CONFLICT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_BID_TYPE;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_BID_UNIT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.NOT_UP_AUCTION;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.SELF_BID_NOT_ALLOWED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long AUCTION_ID = 42L;
    private static final Long BID_ID = 7L;
    private static final long BID_UNIT = 1_000L;
    private static final long CURRENT_PRICE = 231_000L;
    private static final long PRICE = 232_000L;
    private static final LocalDateTime DATABASE_TIME =
            LocalDateTime.of(2026, 7, 30, 12, 34, 55);
    private static final LocalDateTime BID_AT = DATABASE_TIME.plusSeconds(1);

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private Member bidder;

    @Mock
    private Member seller;

    @Mock
    private UpAuction auction;

    @Mock
    private DownAuction downAuction;

    @Mock
    private Bid persistedBid;

    @InjectMocks
    private BidService bidService;

    @Test
    void 조건부_현재가_갱신에_성공하면_UP_입찰을_저장한다() {
        stubSuccessfulUpdate();
        stubPersistedBid();
        when(bidRepository.save(any(Bid.class))).thenReturn(persistedBid);

        BidResult result = bidService.place(
                MEMBER_ID,
                AUCTION_ID,
                BidStatus.UP,
                PRICE
        );

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
    void 밀봉_구간의_SEALED_입찰을_SEALED_상태로_저장한다() {
        stubSuccessfulUpdate(BidStatus.SEALED);
        stubPersistedBid(BidStatus.SEALED);
        when(bidRepository.save(any(Bid.class))).thenReturn(persistedBid);

        BidResult result = bidService.place(
                MEMBER_ID,
                AUCTION_ID,
                BidStatus.SEALED,
                PRICE
        );

        ArgumentCaptor<Bid> bidCaptor = ArgumentCaptor.forClass(Bid.class);
        verify(bidRepository).save(bidCaptor.capture());
        assertAll(
                () -> assertThat(bidCaptor.getValue().getStatus())
                        .isEqualTo(BidStatus.SEALED),
                () -> assertThat(result.status()).isEqualTo(BidStatus.SEALED)
        );
        verify(auctionRepository).updateForSealedBid(
                AUCTION_ID,
                MEMBER_ID,
                PRICE,
                BID_UNIT
        );
        verify(auctionRepository, never()).updateCurrentPriceForUpBid(
                AUCTION_ID,
                MEMBER_ID,
                PRICE,
                BID_UNIT
        );
    }

    @Test
    void UP과_SEALED가_아닌_입찰_유형은_조회_없이_거절한다() {
        BidException exception = assertThrows(
                BidException.class,
                () -> bidService.place(
                        MEMBER_ID,
                        AUCTION_ID,
                        BidStatus.DOWN,
                        PRICE
                )
        );

        assertThat(exception.getErrorCode()).isEqualTo(INVALID_BID_TYPE);
        verifyNoInteractions(memberRepository, auctionRepository, bidRepository);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1_000L, 232_500L})
    void 양수가_아니거나_천원_단위가_아닌_가격은_Repository_호출_없이_거절한다(
            long invalidPrice
    ) {
        BidException exception = assertThrows(
                BidException.class,
                () -> bidService.place(
                        MEMBER_ID,
                        AUCTION_ID,
                        BidStatus.UP,
                        invalidPrice
                )
        );

        assertThat(exception.getErrorCode()).isEqualTo(INVALID_BID_UNIT);
        verifyNoInteractions(memberRepository, auctionRepository, bidRepository);
    }

    @Test
    void 최신_현재가보다_천원_이상_높지_않으면_거절한다() {
        stubFailedUpdate(auction);
        when(auction.getStatus()).thenReturn(AuctionStatus.BID_ONGOING);
        when(auction.getEndedAt()).thenReturn(DATABASE_TIME.plusDays(1));
        when(auction.hasCurrentPrice()).thenReturn(true);
        when(auction.getCurrentPrice()).thenReturn(PRICE);
        when(auctionRepository.currentDatabaseTime()).thenReturn(DATABASE_TIME);
        stubSeller(2L);

        BidException exception = assertThrows(
                BidException.class,
                () -> bidService.place(MEMBER_ID, AUCTION_ID, BidStatus.UP, PRICE)
        );

        assertThat(exception.getErrorCode()).isEqualTo(BID_PRICE_TOO_LOW);
        verifyNoInteractions(memberRepository, bidRepository);
    }

    @Test
    void 존재하지_않는_경매에는_입찰할_수_없다() {
        when(auctionRepository.updateCurrentPriceForUpBid(
                AUCTION_ID,
                MEMBER_ID,
                PRICE,
                BID_UNIT
        ))
                .thenReturn(0);
        when(auctionRepository.findById(AUCTION_ID)).thenReturn(Optional.empty());

        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> bidService.place(MEMBER_ID, AUCTION_ID, BidStatus.UP, PRICE)
        );

        assertThat(exception.getErrorCode()).isEqualTo(AUCTION_NOT_FOUND);
        verifyNoInteractions(memberRepository, bidRepository);
    }

    @Test
    void 하향_경매에는_입찰할_수_없다() {
        stubFailedUpdate(downAuction);

        BidException exception = assertThrows(
                BidException.class,
                () -> bidService.place(MEMBER_ID, AUCTION_ID, BidStatus.UP, PRICE)
        );

        assertThat(exception.getErrorCode()).isEqualTo(NOT_UP_AUCTION);
        verifyNoInteractions(memberRepository, bidRepository);
    }

    @Test
    void 진행_중이_아닌_경매에는_입찰할_수_없다() {
        stubFailedUpdate(auction);
        when(auction.getStatus()).thenReturn(AuctionStatus.COMPLETED);

        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> bidService.place(MEMBER_ID, AUCTION_ID, BidStatus.UP, PRICE)
        );

        assertThat(exception.getErrorCode()).isEqualTo(AUCTION_NOT_ONGOING);
        verifyNoInteractions(memberRepository, bidRepository);
    }

    @Test
    void 종료된_경매에는_입찰할_수_없다() {
        stubFailedUpdate(auction);
        when(auction.getStatus()).thenReturn(AuctionStatus.BID_ONGOING);
        when(auction.getEndedAt()).thenReturn(DATABASE_TIME);
        when(auctionRepository.currentDatabaseTime()).thenReturn(DATABASE_TIME);

        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> bidService.place(MEMBER_ID, AUCTION_ID, BidStatus.UP, PRICE)
        );

        assertThat(exception.getErrorCode()).isEqualTo(AUCTION_ALREADY_ENDED);
        verifyNoInteractions(memberRepository, bidRepository);
    }

    @Test
    void 일반_입찰_구간에서_SEALED_입찰을_거절한다() {
        stubFailedUpdate(auction, BidStatus.SEALED);
        when(auction.getStatus()).thenReturn(AuctionStatus.BID_ONGOING);
        when(auction.getEndedAt()).thenReturn(DATABASE_TIME.plusMinutes(6));
        when(auctionRepository.currentDatabaseTime()).thenReturn(DATABASE_TIME);

        BidException exception = assertThrows(
                BidException.class,
                () -> bidService.place(
                        MEMBER_ID,
                        AUCTION_ID,
                        BidStatus.SEALED,
                        PRICE
                )
        );

        assertThat(exception.getErrorCode()).isEqualTo(BID_PHASE_MISMATCH);
        verifyNoInteractions(memberRepository, bidRepository);
    }

    @Test
    void 정확히_마감_5분_전부터_UP_입찰을_거절한다() {
        stubFailedUpdate(auction);
        when(auction.getStatus()).thenReturn(AuctionStatus.BID_ONGOING);
        when(auction.getEndedAt()).thenReturn(DATABASE_TIME.plusMinutes(5));
        when(auctionRepository.currentDatabaseTime()).thenReturn(DATABASE_TIME);

        BidException exception = assertThrows(
                BidException.class,
                () -> bidService.place(MEMBER_ID, AUCTION_ID, BidStatus.UP, PRICE)
        );

        assertThat(exception.getErrorCode()).isEqualTo(BID_PHASE_MISMATCH);
        verifyNoInteractions(memberRepository, bidRepository);
    }

    @Test
    void 판매자는_자신의_경매에_입찰할_수_없다() {
        stubFailedUpdate(auction);
        when(auction.getStatus()).thenReturn(AuctionStatus.BID_ONGOING);
        when(auction.getEndedAt()).thenReturn(DATABASE_TIME.plusDays(1));
        when(auctionRepository.currentDatabaseTime()).thenReturn(DATABASE_TIME);
        stubSeller(MEMBER_ID);

        BidException exception = assertThrows(
                BidException.class,
                () -> bidService.place(MEMBER_ID, AUCTION_ID, BidStatus.UP, PRICE)
        );

        assertThat(exception.getErrorCode()).isEqualTo(SELF_BID_NOT_ALLOWED);
        verifyNoInteractions(memberRepository, bidRepository);
    }

    @Test
    void 현재가가_long_상한에_가까워도_오버플로_없이_낮은_입찰로_거절한다() {
        long maxUnitPrice = Long.MAX_VALUE - Long.MAX_VALUE % BID_UNIT;
        when(auctionRepository.updateCurrentPriceForUpBid(
                AUCTION_ID,
                MEMBER_ID,
                maxUnitPrice,
                BID_UNIT
        )).thenReturn(0);
        when(auctionRepository.findById(AUCTION_ID)).thenReturn(Optional.of(auction));
        when(auction.getStatus()).thenReturn(AuctionStatus.BID_ONGOING);
        when(auction.getEndedAt()).thenReturn(DATABASE_TIME.plusDays(1));
        when(auctionRepository.currentDatabaseTime()).thenReturn(DATABASE_TIME);
        when(auction.hasCurrentPrice()).thenReturn(true);
        when(auction.getCurrentPrice()).thenReturn(maxUnitPrice);
        stubSeller(2L);

        BidException exception = assertThrows(
                BidException.class,
                () -> bidService.place(
                        MEMBER_ID,
                        AUCTION_ID,
                        BidStatus.UP,
                        maxUnitPrice
                )
        );

        assertThat(exception.getErrorCode()).isEqualTo(BID_PRICE_TOO_LOW);
        verifyNoInteractions(memberRepository, bidRepository);
    }

    @Test
    void 조건부_갱신은_실패했지만_재조회한_조건이_유효하면_동시_입찰_충돌이다() {
        stubFailedUpdate(auction);
        when(auction.getStatus()).thenReturn(AuctionStatus.BID_ONGOING);
        when(auction.getEndedAt()).thenReturn(DATABASE_TIME.plusDays(1));
        when(auction.hasCurrentPrice()).thenReturn(true);
        when(auction.getCurrentPrice()).thenReturn(CURRENT_PRICE);
        when(auctionRepository.currentDatabaseTime()).thenReturn(DATABASE_TIME);
        stubSeller(2L);

        BidException exception = assertThrows(
                BidException.class,
                () -> bidService.place(MEMBER_ID, AUCTION_ID, BidStatus.UP, PRICE)
        );

        assertThat(exception.getErrorCode()).isEqualTo(CONCURRENT_BID_CONFLICT);
        verifyNoInteractions(memberRepository, bidRepository);
    }

    @Test
    void 조건부_갱신의_락_획득에_실패하면_동시_입찰_충돌로_변환한다() {
        when(auctionRepository.updateCurrentPriceForUpBid(
                AUCTION_ID,
                MEMBER_ID,
                PRICE,
                BID_UNIT
        ))
                .thenThrow(new PessimisticLockingFailureException("lock timeout"));

        BidException exception = assertThrows(
                BidException.class,
                () -> bidService.place(MEMBER_ID, AUCTION_ID, BidStatus.UP, PRICE)
        );

        assertThat(exception.getErrorCode()).isEqualTo(CONCURRENT_BID_CONFLICT);
        verifyNoInteractions(memberRepository, bidRepository);
    }

    @Test
    void 조건부_갱신_쿼리의_타임아웃은_동시_입찰_충돌로_변환한다() {
        when(auctionRepository.updateCurrentPriceForUpBid(
                AUCTION_ID,
                MEMBER_ID,
                PRICE,
                BID_UNIT
        ))
                .thenThrow(new QueryTimeoutException("update query timeout"));

        BidException exception = assertThrows(
                BidException.class,
                () -> bidService.place(MEMBER_ID, AUCTION_ID, BidStatus.UP, PRICE)
        );

        assertThat(exception.getErrorCode()).isEqualTo(CONCURRENT_BID_CONFLICT);
        verifyNoInteractions(memberRepository, bidRepository);
    }

    @Test
    void 현재가를_갱신한_뒤_엔티티_참조로_입찰을_저장한다() {
        stubSuccessfulUpdate();
        stubPersistedBid();
        when(bidRepository.save(any(Bid.class))).thenReturn(persistedBid);

        bidService.place(MEMBER_ID, AUCTION_ID, BidStatus.UP, PRICE);

        InOrder order = inOrder(auctionRepository, memberRepository, bidRepository);
        order.verify(auctionRepository).updateCurrentPriceForUpBid(
                AUCTION_ID,
                MEMBER_ID,
                PRICE,
                BID_UNIT
        );
        order.verify(auctionRepository).getReferenceById(AUCTION_ID);
        order.verify(memberRepository).getReferenceById(MEMBER_ID);
        order.verify(bidRepository).save(any(Bid.class));
        verify(auctionRepository, never()).findById(AUCTION_ID);
    }

    private void stubSuccessfulUpdate() {
        stubSuccessfulUpdate(BidStatus.UP);
    }

    private void stubSuccessfulUpdate(BidStatus bidStatus) {
        if (bidStatus == BidStatus.UP) {
            when(auctionRepository.updateCurrentPriceForUpBid(
                    AUCTION_ID,
                    MEMBER_ID,
                    PRICE,
                    BID_UNIT
            )).thenReturn(1);
        } else {
            when(auctionRepository.updateForSealedBid(
                    AUCTION_ID,
                    MEMBER_ID,
                    PRICE,
                    BID_UNIT
            )).thenReturn(1);
        }
        when(auctionRepository.getReferenceById(AUCTION_ID)).thenReturn(auction);
        when(memberRepository.getReferenceById(MEMBER_ID)).thenReturn(bidder);
    }

    private void stubFailedUpdate(Auction failedAuction) {
        stubFailedUpdate(failedAuction, BidStatus.UP);
    }

    private void stubFailedUpdate(Auction failedAuction, BidStatus bidStatus) {
        if (bidStatus == BidStatus.UP) {
            when(auctionRepository.updateCurrentPriceForUpBid(
                    AUCTION_ID,
                    MEMBER_ID,
                    PRICE,
                    BID_UNIT
            )).thenReturn(0);
        } else {
            when(auctionRepository.updateForSealedBid(
                    AUCTION_ID,
                    MEMBER_ID,
                    PRICE,
                    BID_UNIT
            )).thenReturn(0);
        }
        when(auctionRepository.findById(AUCTION_ID)).thenReturn(Optional.of(failedAuction));
    }

    private void stubSeller(long sellerId) {
        when(auction.getSeller()).thenReturn(seller);
        when(seller.getId()).thenReturn(sellerId);
    }

    private void stubPersistedBid() {
        stubPersistedBid(BidStatus.UP);
    }

    private void stubPersistedBid(BidStatus bidStatus) {
        when(persistedBid.getId()).thenReturn(BID_ID);
        when(persistedBid.getAuction()).thenReturn(auction);
        when(auction.getId()).thenReturn(AUCTION_ID);
        when(persistedBid.getBidder()).thenReturn(bidder);
        when(bidder.getId()).thenReturn(MEMBER_ID);
        when(persistedBid.getPrice()).thenReturn(PRICE);
        when(persistedBid.getStatus()).thenReturn(bidStatus);
        when(persistedBid.getCreatedAt()).thenReturn(BID_AT);
    }
}
