package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.Image;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.enums.TradeType;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.ImageRepository;
import com.tikitaka.bidwinback.auction.presentation.dto.request.AuctionCreateRequest;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.exception.MemberException;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import com.tikitaka.bidwinback.upload.domain.PendingAuctionImageStore;
import com.tikitaka.bidwinback.upload.domain.entity.PendingAuctionImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.CATEGORY_NOT_FOUND;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.IMAGE_MIN_COUNT_VIOLATION;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_BUY_NOW_PRICE;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_DURATION;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_IMAGE_REFERENCE;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_INPUT_VALUE;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_MINIMUM_PRICE;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_START_PRICE_UNIT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.MEMBER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionCreateServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long AUCTION_ID = 100L;
    private static final LocalDateTime DB_NOW = LocalDateTime.of(2026, 8, 3, 10, 0, 0);
    private static final List<String> OBJECT_KEYS = List.of("auction-images/a.jpg", "auction-images/b.jpg");

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private PendingAuctionImageStore pendingAuctionImageStore;

    @InjectMocks
    private AuctionCreateService auctionCreateService;

    private Member seller;

    @BeforeEach
    void setUp() {
        seller = Member.builder()
                .name("판매자")
                .phoneNumber("01012345678")
                .nickname("seller")
                .email("seller@example.com")
                .password("encoded")
                .build();
    }

    @Test
    void 상향_경매를_등록하면_UpAuction과_이미지를_저장한다() {
        // given
        stubSellerAndClock();
        stubSaveAssignsId();
        stubOwnedImages();
        AuctionCreateRequest request = upRequest(500_000L);

        // when
        var response = auctionCreateService.create(MEMBER_ID, request);

        // then
        ArgumentCaptor<Auction> auctionCaptor = ArgumentCaptor.forClass(Auction.class);
        verify(auctionRepository).save(auctionCaptor.capture());
        Auction saved = auctionCaptor.getValue();
        UpAuction upAuction = (UpAuction) saved;

        assertAll(
                () -> assertThat(upAuction.getSeller()).isSameAs(seller),
                () -> assertThat(upAuction.getTitle()).isEqualTo("아이패드 팝니다"),
                () -> assertThat(upAuction.getStartPrice()).isEqualTo(200_000L),
                () -> assertThat(upAuction.getEndedAt()).isEqualTo(DB_NOW.plusMinutes(60)),
                () -> assertThat(upAuction.getTradeType()).isEqualTo(TradeType.DELIVERY),
                () -> assertThat(upAuction.getBuyNowPrice()).isEqualTo(500_000L),
                () -> assertThat(response.auctionId()).isEqualTo(AUCTION_ID)
        );
        assertImagesSavedInOrder(saved);
        verify(pendingAuctionImageStore).deleteByObjectKeyIn(OBJECT_KEYS);
    }

    @Test
    void 하향_경매를_등록하면_DownAuction을_저장한다() {
        // given
        stubSellerAndClock();
        stubSaveAssignsId();
        stubOwnedImages();
        AuctionCreateRequest request = downRequest(150_000L, 10_000L, 30L);

        // when
        var response = auctionCreateService.create(MEMBER_ID, request);

        // then
        ArgumentCaptor<Auction> auctionCaptor = ArgumentCaptor.forClass(Auction.class);
        verify(auctionRepository).save(auctionCaptor.capture());
        DownAuction downAuction = (DownAuction) auctionCaptor.getValue();

        assertAll(
                () -> assertThat(downAuction.getMinimumPrice()).isEqualTo(150_000L),
                () -> assertThat(downAuction.getDropPrice()).isEqualTo(10_000L),
                () -> assertThat(downAuction.getPriceDropInterval()).isEqualTo(30L),
                () -> assertThat(response.auctionId()).isEqualTo(AUCTION_ID)
        );
    }

    @Test
    void 존재하지_않는_회원은_경매를_등록할_수_없다() {
        // given
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        // when
        MemberException exception = assertThrows(
                MemberException.class,
                () -> auctionCreateService.create(MEMBER_ID, upRequest(null))
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(MEMBER_NOT_FOUND);
        verifyNoInteractions(auctionRepository, imageRepository, pendingAuctionImageStore);
    }

    @Test
    void 존재하지_않는_카테고리는_등록할_수_없다() {
        // given
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));
        AuctionCreateRequest request = withCategory(upRequest(null), "INVALID");

        // when
        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, request)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(CATEGORY_NOT_FOUND);
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void 허용되지_않은_진행_시간은_등록할_수_없다() {
        // given
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));
        AuctionCreateRequest request = withDuration(upRequest(null), 45);

        // when
        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, request)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(INVALID_DURATION);
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void 시작가가_1000원_단위가_아니면_등록할_수_없다() {
        // given
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));
        AuctionCreateRequest request = withStartPrice(upRequest(null), 200_500L);

        // when
        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, request)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(INVALID_START_PRICE_UNIT);
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void 즉시구매가는_시작가보다_높아야_한다() {
        // given
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));
        AuctionCreateRequest request = upRequest(200_000L);

        // when
        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, request)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(INVALID_BUY_NOW_PRICE);
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void 최저가는_시작가보다_낮아야_한다() {
        // given
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));
        AuctionCreateRequest request = downRequest(200_000L, 10_000L, 30L);

        // when
        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, request)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(INVALID_MINIMUM_PRICE);
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void 하향_경매는_최저가_인하금액_인하주기가_모두_있어야_한다() {
        // given
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));
        AuctionCreateRequest request = downRequest(150_000L, null, 30L);

        // when
        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, request)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(INVALID_INPUT_VALUE);
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void 이미지가_없으면_등록할_수_없다() {
        // given
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));
        AuctionCreateRequest request = withImages(upRequest(500_000L), List.of());

        // when
        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, request)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(IMAGE_MIN_COUNT_VIOLATION);
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void 본인이_업로드하지_않은_이미지는_사용할_수_없다() {
        // given
        stubSellerAndClock();
        stubSaveAssignsId();
        when(pendingAuctionImageStore.findByMemberIdAndObjectKeyIn(MEMBER_ID, OBJECT_KEYS))
                .thenReturn(List.of(PendingAuctionImage.issue(MEMBER_ID, UUID.randomUUID(), OBJECT_KEYS.get(0))));
        AuctionCreateRequest request = upRequest(500_000L);

        // when
        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, request)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(INVALID_IMAGE_REFERENCE);
        verify(imageRepository, never()).saveAll(anyList());
        verify(pendingAuctionImageStore, never()).deleteByObjectKeyIn(anyList());
    }

    private void stubSellerAndClock() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));
        when(auctionRepository.currentDatabaseTime()).thenReturn(DB_NOW);
    }

    private void stubSaveAssignsId() {
        when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> {
            Auction saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", AUCTION_ID);
            return saved;
        });
    }

    private void stubOwnedImages() {
        List<PendingAuctionImage> pendingImages = OBJECT_KEYS.stream()
                .map(objectKey -> PendingAuctionImage.issue(MEMBER_ID, UUID.randomUUID(), objectKey))
                .toList();
        when(pendingAuctionImageStore.findByMemberIdAndObjectKeyIn(eq(MEMBER_ID), anyList()))
                .thenReturn(pendingImages);
    }

    private void assertImagesSavedInOrder(Auction auction) {
        ArgumentCaptor<List<Image>> imagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(imageRepository).saveAll(imagesCaptor.capture());
        List<Image> savedImages = imagesCaptor.getValue();

        assertThat(savedImages).hasSize(OBJECT_KEYS.size());
        for (int i = 0; i < OBJECT_KEYS.size(); i++) {
            assertThat(savedImages.get(i).getObjectKey()).isEqualTo(OBJECT_KEYS.get(i));
            assertThat(savedImages.get(i).getAuction()).isSameAs(auction);
        }
    }

    private AuctionCreateRequest upRequest(Long buyNowPrice) {
        return new AuctionCreateRequest(
                "아이패드 팝니다",
                "미개봉 새 제품입니다.",
                "HOUSEHOLD",
                "01098765432",
                AuctionType.UP,
                TradeType.DELIVERY,
                60,
                200_000L,
                buyNowPrice,
                null,
                null,
                null,
                OBJECT_KEYS
        );
    }

    private AuctionCreateRequest downRequest(
            Long minimumPrice,
            Long dropPrice,
            Long priceDropInterval
    ) {
        return new AuctionCreateRequest(
                "냉장고 급처합니다",
                "이사 정리로 급처합니다.",
                "HOUSEHOLD",
                "01098765432",
                AuctionType.DOWN,
                TradeType.DIRECT,
                60,
                200_000L,
                null,
                minimumPrice,
                dropPrice,
                priceDropInterval,
                OBJECT_KEYS
        );
    }

    private AuctionCreateRequest withCategory(AuctionCreateRequest request, String category) {
        return new AuctionCreateRequest(
                request.title(), request.description(), category, request.contact(),
                request.auctionType(), request.tradeType(), request.durationMinutes(), request.startPrice(),
                request.buyNowPrice(), request.minimumPrice(), request.dropPrice(), request.priceDropInterval(),
                request.images()
        );
    }

    private AuctionCreateRequest withDuration(AuctionCreateRequest request, int durationMinutes) {
        return new AuctionCreateRequest(
                request.title(), request.description(), request.category(), request.contact(),
                request.auctionType(), request.tradeType(), durationMinutes, request.startPrice(),
                request.buyNowPrice(), request.minimumPrice(), request.dropPrice(), request.priceDropInterval(),
                request.images()
        );
    }

    private AuctionCreateRequest withStartPrice(AuctionCreateRequest request, long startPrice) {
        return new AuctionCreateRequest(
                request.title(), request.description(), request.category(), request.contact(),
                request.auctionType(), request.tradeType(), request.durationMinutes(), startPrice,
                request.buyNowPrice(), request.minimumPrice(), request.dropPrice(), request.priceDropInterval(),
                request.images()
        );
    }

    private AuctionCreateRequest withImages(AuctionCreateRequest request, List<String> images) {
        return new AuctionCreateRequest(
                request.title(), request.description(), request.category(), request.contact(),
                request.auctionType(), request.tradeType(), request.durationMinutes(), request.startPrice(),
                request.buyNowPrice(), request.minimumPrice(), request.dropPrice(), request.priceDropInterval(),
                images
        );
    }
}
