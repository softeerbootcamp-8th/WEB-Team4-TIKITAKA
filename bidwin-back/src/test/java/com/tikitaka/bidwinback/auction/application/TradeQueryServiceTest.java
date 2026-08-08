package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.AuctionTrade;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.enums.TradeStatus;
import com.tikitaka.bidwinback.auction.domain.enums.TradeType;
import com.tikitaka.bidwinback.auction.domain.exception.TradeException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionTradeRepository;
import com.tikitaka.bidwinback.auction.domain.repository.ImageRepository;
import com.tikitaka.bidwinback.auction.domain.repository.dto.AuctionThumbnailRow;
import com.tikitaka.bidwinback.auction.presentation.dto.response.TradeDetailResponse;
import com.tikitaka.bidwinback.global.storage.ImageUrlResolver;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.TRADE_ACCESS_DENIED;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.TRADE_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeQueryServiceTest {

    private static final long TRADE_ID = 7L;
    private static final long AUCTION_ID = 42L;
    private static final long BUYER_ID = 1L;
    private static final long SELLER_ID = 2L;
    private static final long OUTSIDER_ID = 3L;
    private static final long FINAL_PRICE = 200_000L;
    private static final String CONTACT = "01012345678";
    private static final String THUMBNAIL_KEY = "auctions/42/thumbnail.png";
    private static final String THUMBNAIL_URL = "https://cdn.example.com/thumbnail.png";
    private static final LocalDateTime PURCHASED_AT = LocalDateTime.of(2026, 8, 5, 12, 0);

    @Mock
    private AuctionTradeRepository auctionTradeRepository;
    @Mock
    private ImageRepository imageRepository;
    @Mock
    private ImageUrlResolver imageUrlResolver;

    private TradeQueryService tradeQueryService;

    @BeforeEach
    void setUp() {
        tradeQueryService = new TradeQueryService(
                auctionTradeRepository,
                imageRepository,
                imageUrlResolver
        );
    }

    @Test
    void 참여한_구매자는_거래_상세와_대표_이미지를_조회한다() {
        // given
        AuctionTrade trade = trade(TradeStatus.WAITING_CONFIRM);
        when(auctionTradeRepository.findDetailById(TRADE_ID)).thenReturn(Optional.of(trade));
        when(imageRepository.findRepresentativeThumbnails(Set.of(AUCTION_ID)))
                .thenReturn(List.of(new AuctionThumbnailRow(AUCTION_ID, THUMBNAIL_KEY)));
        when(imageUrlResolver.resolve(THUMBNAIL_KEY)).thenReturn(THUMBNAIL_URL);

        // when
        TradeDetailResponse response = tradeQueryService.getTradeDetail(BUYER_ID, TRADE_ID);

        // then
        assertAll(
                () -> assertThat(response.tradeId()).isEqualTo(TRADE_ID),
                () -> assertThat(response.auctionId()).isEqualTo(AUCTION_ID),
                () -> assertThat(response.title()).isEqualTo("경매 상품"),
                () -> assertThat(response.thumbnailUrl()).isEqualTo(THUMBNAIL_URL),
                () -> assertThat(response.auctionType()).isEqualTo(AuctionType.UP),
                () -> assertThat(response.status()).isEqualTo(TradeStatus.WAITING_CONFIRM),
                () -> assertThat(response.role()).isEqualTo("BUYER"),
                () -> assertThat(response.finalPrice()).isEqualTo(FINAL_PRICE),
                () -> assertThat(response.purchasedAt()).isEqualTo(1_785_898_800_000L)
        );
    }

    @Test
    void 구매확정_전에는_구매자에게도_판매자_연락처를_숨긴다() {
        // given
        AuctionTrade trade = trade(TradeStatus.WAITING_CONFIRM);
        stubTrade(trade);

        // when
        TradeDetailResponse response = tradeQueryService.getTradeDetail(BUYER_ID, TRADE_ID);

        // then
        assertThat(response.sellerContact()).isNull();
    }

    @Test
    void 구매확정_후에는_구매자에게_판매자_연락처를_공개한다() {
        // given
        AuctionTrade trade = trade(TradeStatus.CONFIRMED);
        stubTrade(trade);

        // when
        TradeDetailResponse response = tradeQueryService.getTradeDetail(BUYER_ID, TRADE_ID);

        // then
        assertThat(response.sellerContact()).isEqualTo(CONTACT);
    }

    @Test
    void 거래완료_후에도_구매자에게_판매자_연락처를_공개한다() {
        // given
        AuctionTrade trade = trade(TradeStatus.COMPLETED);
        stubTrade(trade);

        // when
        TradeDetailResponse response = tradeQueryService.getTradeDetail(BUYER_ID, TRADE_ID);

        // then
        assertThat(response.sellerContact()).isEqualTo(CONTACT);
    }

    @Test
    void 판매자에게는_구매확정_후에도_연락처를_응답하지_않는다() {
        // given
        AuctionTrade trade = trade(TradeStatus.CONFIRMED);
        stubTrade(trade);

        // when
        TradeDetailResponse response = tradeQueryService.getTradeDetail(SELLER_ID, TRADE_ID);

        // then
        assertAll(
                () -> assertThat(response.role()).isEqualTo("SELLER"),
                () -> assertThat(response.sellerContact()).isNull()
        );
    }

    @Test
    void 거래_참여자가_아니면_상세_조회를_거부한다() {
        // given
        AuctionTrade trade = trade(TradeStatus.CONFIRMED);
        when(auctionTradeRepository.findDetailById(TRADE_ID)).thenReturn(Optional.of(trade));

        // when & then
        assertThatThrownBy(() -> tradeQueryService.getTradeDetail(OUTSIDER_ID, TRADE_ID))
                .isInstanceOfSatisfying(TradeException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(TRADE_ACCESS_DENIED)
                );
        verifyNoInteractions(imageRepository, imageUrlResolver);
    }

    @Test
    void 거래_참여자가_아니면_SSE_구독_검증을_통과하지_못한다() {
        // given
        AuctionTrade trade = trade(TradeStatus.CONFIRMED);
        when(auctionTradeRepository.findDetailById(TRADE_ID)).thenReturn(Optional.of(trade));

        // when & then
        assertThatThrownBy(() -> tradeQueryService.verifyParticipant(OUTSIDER_ID, TRADE_ID))
                .isInstanceOfSatisfying(TradeException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(TRADE_ACCESS_DENIED)
                );
    }

    @Test
    void 존재하지_않는_거래는_상세_조회를_거부한다() {
        // given
        when(auctionTradeRepository.findDetailById(TRADE_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> tradeQueryService.getTradeDetail(BUYER_ID, TRADE_ID))
                .isInstanceOfSatisfying(TradeException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(TRADE_NOT_FOUND)
                );
        verifyNoInteractions(imageRepository, imageUrlResolver);
    }

    private void stubTrade(AuctionTrade trade) {
        when(auctionTradeRepository.findDetailById(TRADE_ID)).thenReturn(Optional.of(trade));
        when(imageRepository.findRepresentativeThumbnails(Set.of(AUCTION_ID)))
                .thenReturn(List.of());
    }

    private AuctionTrade trade(TradeStatus status) {
        Member buyer = member(BUYER_ID, "구매자");
        Member seller = member(SELLER_ID, "판매자");
        UpAuction auction = UpAuction.builder()
                .seller(seller)
                .title("경매 상품")
                .description("설명")
                .status(null)
                .category(AuctionCategory.HOUSEHOLD)
                .startPrice(100_000L)
                .endedAt(PURCHASED_AT.plusDays(1))
                .tradeType(TradeType.DIRECT)
                .contact(CONTACT)
                .buyNowPrice(null)
                .build();
        ReflectionTestUtils.setField(auction, "id", AUCTION_ID);

        AuctionTrade trade = AuctionTrade.builder()
                .auction(auction)
                .buyer(buyer)
                .status(status)
                .finalPrice(FINAL_PRICE)
                .purchasedAt(PURCHASED_AT)
                .build();
        ReflectionTestUtils.setField(trade, "id", TRADE_ID);
        return trade;
    }

    private Member member(long id, String nickname) {
        Member member = Member.builder()
                .name(nickname)
                .phoneNumber("01012345678")
                .nickname(nickname)
                .email(nickname + "@example.com")
                .password("encoded-password")
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
