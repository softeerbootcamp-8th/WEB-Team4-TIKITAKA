package com.tikitaka.bidwinback.member.application;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionStatus;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionTradeRepository;
import com.tikitaka.bidwinback.auction.domain.repository.BidRepository;
import com.tikitaka.bidwinback.auction.domain.repository.ImageRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionThumbnailRow;
import com.tikitaka.bidwinback.global.exception.ErrorCode;
import com.tikitaka.bidwinback.global.storage.ImageUrlResolver;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.exception.MemberException;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import com.tikitaka.bidwinback.member.presentation.dto.response.ActiveTradeResponse;
import com.tikitaka.bidwinback.member.presentation.dto.response.BuyingItemResponse;
import com.tikitaka.bidwinback.member.presentation.dto.response.MyPageResponse;
import com.tikitaka.bidwinback.member.presentation.dto.response.SellingItemResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyPageServiceTest {

    private static final long MEMBER_ID = 7L;
    private static final String NICKNAME = "급처하는근성";
    private static final String PROFILE_KEY = "profiles/p.png";
    private static final String PROFILE_URL = "https://cdn.example.com/profiles/p.png";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 7, 19, 9, 0);

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private AuctionTradeRepository auctionTradeRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private ImageUrlResolver imageUrlResolver;

    @Mock
    private Member member;

    @Mock
    private UpAuction upAuction;

    @Mock
    private DownAuction downAuction;

    @Mock
    private AuctionTrade trade;

    @Mock
    private Member counterparty;

    @InjectMocks
    private MyPageService myPageService;

    @Test
    void 마이페이지를_조회하면_프로필과_보증금을_매핑한다() {
        stubProfileBasics();
        stubEmptyLists();

        MyPageResponse response = myPageService.getMyPage(MEMBER_ID);

        assertThat(response.profile().nickname()).isEqualTo(NICKNAME);
        assertThat(response.profile().profileImageUrl()).isEqualTo(PROFILE_URL);
        assertThat(response.profile().joinedAt()).isEqualTo(toEpochMilli(CREATED_AT));
        assertThat(response.profile().sellCount()).isEqualTo(17L);
        assertThat(response.profile().auctionJoinCount()).isEqualTo(20L);
        assertThat(response.deposit().balance()).isEqualTo(60_000L);
        assertThat(response.deposit().inUse()).isEqualTo(16_000L);
        assertThat(response.activeTrades()).isEmpty();
        assertThat(response.sellingItems()).isEmpty();
        assertThat(response.buyingItems()).isEmpty();
    }

    @Test
    void 판매_물품의_유형_상태_썸네일을_매핑한다() {
        stubProfileBasics();
        when(auctionRepository.findTop3BySellerIdOrderByIdDesc(MEMBER_ID))
                .thenReturn(List.of(upAuction));
        when(auctionTradeRepository.findBuyingItems(eq(MEMBER_ID), any()))
                .thenReturn(List.of());
        when(auctionTradeRepository.findActiveTrades(
                MEMBER_ID,
                TradeStatus.WAITING_CONFIRM,
                TradeStatus.CONFIRMED
        ))
                .thenReturn(List.of());
        when(upAuction.getId()).thenReturn(201L);
        when(upAuction.getTitle()).thenReturn("애플워치 SE 40mm");
        when(upAuction.getStartPrice()).thenReturn(150_000L);
        when(upAuction.getStatus()).thenReturn(AuctionStatus.COMPLETED);
        when(auctionTradeRepository.findFinalPriceByAuctionId(201L))
                .thenReturn(Optional.of(180_000L));
        when(imageRepository.findRepresentativeThumbnails(any()))
                .thenReturn(List.of(new AuctionThumbnailRow(201L, "auctions/201.png")));
        when(imageUrlResolver.resolve("auctions/201.png"))
                .thenReturn("https://cdn.example.com/auctions/201.png");

        SellingItemResponse item = myPageService.getMyPage(MEMBER_ID).sellingItems().getFirst();

        assertThat(item.auctionId()).isEqualTo(201L);
        assertThat(item.auctionType()).isEqualTo("UP");
        assertThat(item.startPrice()).isEqualTo(150_000L);
        assertThat(item.price()).isEqualTo(180_000L);
        assertThat(item.status()).isEqualTo("SOLD");
        assertThat(item.thumbnailUrl()).isEqualTo("https://cdn.example.com/auctions/201.png");
    }

    @Test
    void 진행_중인_하향_판매_물품은_가격_계산_정보를_응답한다() {
        stubProfileBasics();
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 6, 12, 0);
        when(auctionRepository.findTop3BySellerIdOrderByIdDesc(MEMBER_ID))
                .thenReturn(List.of(downAuction));
        when(auctionTradeRepository.findBuyingItems(eq(MEMBER_ID), any()))
                .thenReturn(List.of());
        when(auctionTradeRepository.findActiveTrades(
                MEMBER_ID,
                TradeStatus.WAITING_CONFIRM,
                TradeStatus.CONFIRMED
        )).thenReturn(List.of());
        when(downAuction.getId()).thenReturn(202L);
        when(downAuction.getTitle()).thenReturn("하향 경매");
        when(downAuction.getStartPrice()).thenReturn(100_000L);
        when(downAuction.getCurrentPrice()).thenReturn(100_000L);
        when(downAuction.getStatus()).thenReturn(AuctionStatus.OPEN);
        when(downAuction.getMinimumPrice()).thenReturn(40_000L);
        when(downAuction.getDropPrice()).thenReturn(5_000L);
        when(downAuction.getPriceDropInterval()).thenReturn(10L);
        when(downAuction.getStartedAt()).thenReturn(startedAt);
        when(imageRepository.findRepresentativeThumbnails(any())).thenReturn(List.of());

        SellingItemResponse item = myPageService.getMyPage(MEMBER_ID).sellingItems().getFirst();

        assertThat(item.downPricing()).isNotNull();
        assertThat(item.downPricing().minimumPrice()).isEqualTo(40_000L);
        assertThat(item.downPricing().dropPrice()).isEqualTo(5_000L);
        assertThat(item.downPricing().priceDropIntervalMs()).isEqualTo(600_000L);
        assertThat(item.downPricing().startedAt()).isEqualTo(toEpochMilli(startedAt));
    }

    @Test
    void 진행_중_거래에서_구매자면_BUYER로_매핑한다() {
        stubProfileBasics();
        stubActiveTrade();
        when(counterparty.getId()).thenReturn(MEMBER_ID); // 내가 구매자
        when(trade.getStatus()).thenReturn(TradeStatus.WAITING_CONFIRM);

        ActiveTradeResponse active = myPageService.getMyPage(MEMBER_ID).activeTrades().getFirst();

        assertThat(active.tradeId()).isEqualTo(1L);
        assertThat(active.auctionId()).isEqualTo(90L);
        assertThat(active.role()).isEqualTo("BUYER");
        assertThat(active.status()).isEqualTo("PAYMENT_PENDING");
        assertThat(active.price()).isEqualTo(265_000L);
    }

    @Test
    void CONFIRMED_거래의_판매자면_SELLER와_IN_PROGRESS로_매핑한다() {
        stubProfileBasics();
        stubActiveTrade();
        when(counterparty.getId()).thenReturn(99L); // 내가 판매자
        when(trade.getStatus()).thenReturn(TradeStatus.CONFIRMED);

        ActiveTradeResponse active = myPageService.getMyPage(MEMBER_ID).activeTrades().getFirst();

        assertThat(active.role()).isEqualTo("SELLER");
        assertThat(active.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void 구매_물품의_상태와_유형을_매핑한다() {
        stubProfileBasics();
        when(auctionRepository.findTop3BySellerIdOrderByIdDesc(MEMBER_ID))
                .thenReturn(List.of());
        when(auctionTradeRepository.findActiveTrades(
                MEMBER_ID,
                TradeStatus.WAITING_CONFIRM,
                TradeStatus.CONFIRMED
        ))
                .thenReturn(List.of());
        when(auctionTradeRepository.findBuyingItems(eq(MEMBER_ID), any()))
                .thenReturn(List.of(trade));
        when(trade.getAuction()).thenReturn(downAuction);
        when(trade.getFinalPrice()).thenReturn(98_000L);
        when(trade.getStatus()).thenReturn(TradeStatus.CONFIRMED);
        when(downAuction.getId()).thenReturn(91L);
        when(downAuction.getTitle()).thenReturn("캠핑 4인용 텐트 풀세트");
        when(downAuction.getStartPrice()).thenReturn(140_000L);
        when(imageRepository.findRepresentativeThumbnails(any())).thenReturn(List.of());

        BuyingItemResponse item = myPageService.getMyPage(MEMBER_ID).buyingItems().getFirst();

        assertThat(item.auctionId()).isEqualTo(91L);
        assertThat(item.auctionType()).isEqualTo("DOWN");
        assertThat(item.startPrice()).isEqualTo(140_000L);
        assertThat(item.price()).isEqualTo(98_000L);
        assertThat(item.status()).isEqualTo("IN_PROGRESS");
        assertThat(item.thumbnailUrl()).isNull();
    }

    @Test
    void 존재하지_않는_회원이면_예외를_던진다() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> myPageService.getMyPage(MEMBER_ID))
                .isInstanceOf(MemberException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    private void stubProfileBasics() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getId()).thenReturn(MEMBER_ID);
        when(member.getNickname()).thenReturn(NICKNAME);
        when(member.getProfileObjectKey()).thenReturn(PROFILE_KEY);
        when(member.getCreatedAt()).thenReturn(CREATED_AT);
        when(member.getTotalPoint()).thenReturn(60_000L);
        when(member.getLockedPoint()).thenReturn(16_000L);
        when(auctionTradeRepository.countByAuctionSellerIdAndStatus(MEMBER_ID, TradeStatus.COMPLETED))
                .thenReturn(17L);
        when(bidRepository.countDistinctAuctionByBidderId(MEMBER_ID)).thenReturn(20L);
        when(imageUrlResolver.resolve(PROFILE_KEY)).thenReturn(PROFILE_URL);
    }

    private void stubEmptyLists() {
        when(auctionRepository.findTop3BySellerIdOrderByIdDesc(MEMBER_ID)).thenReturn(List.of());
        when(auctionTradeRepository.findBuyingItems(eq(MEMBER_ID), any())).thenReturn(List.of());
        when(auctionTradeRepository.findActiveTrades(
                MEMBER_ID,
                TradeStatus.WAITING_CONFIRM,
                TradeStatus.CONFIRMED
        ))
                .thenReturn(List.of());
    }

    private void stubActiveTrade() {
        when(auctionRepository.findTop3BySellerIdOrderByIdDesc(MEMBER_ID)).thenReturn(List.of());
        when(auctionTradeRepository.findBuyingItems(eq(MEMBER_ID), any())).thenReturn(List.of());
        when(auctionTradeRepository.findActiveTrades(
                MEMBER_ID,
                TradeStatus.WAITING_CONFIRM,
                TradeStatus.CONFIRMED
        ))
                .thenReturn(List.of(trade));
        when(trade.getId()).thenReturn(1L);
        when(trade.getAuction()).thenReturn(upAuction);
        when(trade.getBuyer()).thenReturn(counterparty);
        when(trade.getFinalPrice()).thenReturn(265_000L);
        when(upAuction.getId()).thenReturn(90L);
        when(upAuction.getTitle()).thenReturn("닌텐도 스위치 OLED + 게임 3종");
        when(imageRepository.findRepresentativeThumbnails(any())).thenReturn(List.of());
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli();
    }
}
