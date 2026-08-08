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
import com.tikitaka.bidwinback.global.storage.ObjectStorage;
import com.tikitaka.bidwinback.global.storage.StoredObjectMetadata;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import com.tikitaka.bidwinback.member.domain.exception.MemberException;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import com.tikitaka.bidwinback.upload.application.AuctionImageObjectKeyGenerator;
import com.tikitaka.bidwinback.upload.application.AuctionImageStorageCleanup;
import com.tikitaka.bidwinback.upload.domain.AuctionImageFileType;
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
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_PRICE_UNIT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_START_PRICE_UNIT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.MEMBER_NOT_ACTIVE;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.MEMBER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionCreateServiceTest {

    private static final long MEMBER_ID = 1L;
    private static final long AUCTION_ID = 100L;
    private static final LocalDateTime DB_NOW = LocalDateTime.of(2026, 8, 3, 10, 0, 0);
    private static final UUID DRAFT_ID = UUID.fromString("44eac1aa-827d-40c3-b3e9-c44abb94ed09");
    private static final UUID FIRST_UPLOAD_ID = UUID.fromString("a2ddf707-cc3b-43d0-8c92-b86e8da74bc6");
    private static final UUID SECOND_UPLOAD_ID = UUID.fromString("f6822a2e-d7ad-4896-a801-1524c81eb6b2");
    private static final List<UUID> UPLOAD_IDS = List.of(FIRST_UPLOAD_ID, SECOND_UPLOAD_ID);
    private static final String FIRST_TEMPORARY_KEY = "temp/a2ddf707-cc3b-43d0-8c92-b86e8da74bc6";
    private static final String SECOND_TEMPORARY_KEY = "temp/f6822a2e-d7ad-4896-a801-1524c81eb6b2";
    private static final List<String> TEMPORARY_KEYS = List.of(FIRST_TEMPORARY_KEY, SECOND_TEMPORARY_KEY);
    private static final String FIRST_CHECKSUM = "mH2LpXfVw2f2Y87a5SIc1J5m3eS74iqrAx/CBEhb3c4=";
    private static final String SECOND_CHECKSUM = "YgVvBrlqJ7qG0u/UokhAn3lVnI5PThR2Y7Nk2cQ7QzE=";

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private PendingAuctionImageStore pendingAuctionImageStore;

    @Mock
    private AuctionImageObjectKeyGenerator imageObjectKeyGenerator;

    @Mock
    private ObjectStorage objectStorage;

    @Mock
    private AuctionImageStorageCleanup imageStorageCleanup;

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
                .status(MemberStatus.ACTIVE)
                .build();
    }

    @Test
    void 본인_draft의_uploadId가_가리키는_검증된_임시_객체를_영구_객체로_복사해_저장한다() {
        stubSellerAndClock();
        stubSaveAssignsId();
        List<PendingAuctionImage> pendingImages = ownedPendingImages();
        when(pendingAuctionImageStore.findByMemberIdAndDraftIdAndUploadIdInForUpdate(
                MEMBER_ID, DRAFT_ID, UPLOAD_IDS
        )).thenReturn(pendingImages);
        stubMatchingMetadata(pendingImages);
        String firstPermanentKey = "auction-images/100/a2ddf707-cc3b-43d0-8c92-b86e8da74bc6.jpg";
        String secondPermanentKey = "auction-images/100/f6822a2e-d7ad-4896-a801-1524c81eb6b2.png";
        when(imageObjectKeyGenerator.generatePermanent(
                AUCTION_ID, FIRST_UPLOAD_ID, AuctionImageFileType.JPEG
        )).thenReturn(firstPermanentKey);
        when(imageObjectKeyGenerator.generatePermanent(
                AUCTION_ID, SECOND_UPLOAD_ID, AuctionImageFileType.PNG
        )).thenReturn(secondPermanentKey);

        var response = auctionCreateService.create(MEMBER_ID, upRequest(500_000L));

        ArgumentCaptor<Auction> auctionCaptor = ArgumentCaptor.forClass(Auction.class);
        verify(auctionRepository).save(auctionCaptor.capture());
        UpAuction savedAuction = (UpAuction) auctionCaptor.getValue();
        assertAll(
                () -> assertThat(savedAuction.getSeller()).isSameAs(seller),
                () -> assertThat(savedAuction.getEndedAt()).isEqualTo(DB_NOW.plusMinutes(60)),
                () -> assertThat(response.auctionId()).isEqualTo(AUCTION_ID)
        );
        verify(pendingAuctionImageStore).findByMemberIdAndDraftIdAndUploadIdInForUpdate(
                MEMBER_ID, DRAFT_ID, UPLOAD_IDS
        );
        verify(objectStorage).head(FIRST_TEMPORARY_KEY);
        verify(objectStorage).head(SECOND_TEMPORARY_KEY);
        verify(objectStorage).copy(FIRST_TEMPORARY_KEY, firstPermanentKey);
        verify(objectStorage).copy(SECOND_TEMPORARY_KEY, secondPermanentKey);
        verify(imageObjectKeyGenerator).generatePermanent(
                AUCTION_ID, FIRST_UPLOAD_ID, AuctionImageFileType.JPEG
        );
        verify(imageObjectKeyGenerator).generatePermanent(
                AUCTION_ID, SECOND_UPLOAD_ID, AuctionImageFileType.PNG
        );
        assertImagesSavedInOrder(savedAuction, List.of(firstPermanentKey, secondPermanentKey));
        verify(pendingAuctionImageStore).deleteByObjectKeyIn(TEMPORARY_KEYS);

        ArgumentCaptor<List<String>> temporaryKeysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> promotedKeysCaptor = ArgumentCaptor.forClass(List.class);
        verify(imageStorageCleanup).register(
                temporaryKeysCaptor.capture(),
                promotedKeysCaptor.capture()
        );
        assertThat(temporaryKeysCaptor.getValue()).containsExactlyElementsOf(TEMPORARY_KEYS);
        assertThat(promotedKeysCaptor.getValue())
                .containsExactly(firstPermanentKey, secondPermanentKey);
    }

    @Test
    void 다른_회원이나_draft의_uploadId는_경매_이미지로_사용할_수_없다() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));
        when(pendingAuctionImageStore.findByMemberIdAndDraftIdAndUploadIdInForUpdate(
                MEMBER_ID, DRAFT_ID, UPLOAD_IDS
        )).thenReturn(List.of(ownedPendingImages().getFirst()));

        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, upRequest(500_000L))
        );

        assertThat(exception.getErrorCode()).isEqualTo(INVALID_IMAGE_REFERENCE);
        verifyNoInteractions(objectStorage, imageObjectKeyGenerator, imageStorageCleanup, imageRepository);
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void 다른_draft에_속한_uploadId는_사용할_수_없다() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));
        when(pendingAuctionImageStore.findByMemberIdAndDraftIdAndUploadIdInForUpdate(
                MEMBER_ID, DRAFT_ID, UPLOAD_IDS
        )).thenReturn(List.of());

        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, upRequest(500_000L))
        );

        assertThat(exception.getErrorCode()).isEqualTo(INVALID_IMAGE_REFERENCE);
        verifyNoInteractions(objectStorage, imageObjectKeyGenerator, imageStorageCleanup, imageRepository);
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void S3_객체의_크기_형식_체크섬이_예약_정보와_다르면_등록하지_않는다() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));
        List<PendingAuctionImage> pendingImages = ownedPendingImages();
        when(pendingAuctionImageStore.findByMemberIdAndDraftIdAndUploadIdInForUpdate(
                MEMBER_ID, DRAFT_ID, UPLOAD_IDS
        )).thenReturn(pendingImages);
        when(objectStorage.head(FIRST_TEMPORARY_KEY)).thenReturn(Optional.of(
                new StoredObjectMetadata(248_392L, "image/jpeg", "different-checksum")
        ));

        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, upRequest(500_000L))
        );

        assertThat(exception.getErrorCode()).isEqualTo(INVALID_IMAGE_REFERENCE);
        verify(objectStorage).head(FIRST_TEMPORARY_KEY);
        verify(objectStorage, never()).copy(any(), any());
        verifyNoInteractions(imageObjectKeyGenerator, imageStorageCleanup, imageRepository);
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void 업로드된_S3_객체가_없으면_등록하지_않는다() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));
        List<PendingAuctionImage> pendingImages = ownedPendingImages();
        when(pendingAuctionImageStore.findByMemberIdAndDraftIdAndUploadIdInForUpdate(
                MEMBER_ID, DRAFT_ID, UPLOAD_IDS
        )).thenReturn(pendingImages);
        when(objectStorage.head(FIRST_TEMPORARY_KEY)).thenReturn(Optional.empty());

        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, upRequest(500_000L))
        );

        assertThat(exception.getErrorCode()).isEqualTo(INVALID_IMAGE_REFERENCE);
        verify(objectStorage).head(FIRST_TEMPORARY_KEY);
        verify(objectStorage, never()).copy(any(), any());
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void 하향_경매를_등록하면_DownAuction을_저장한다() {
        stubSellerAndClock();
        stubSaveAssignsId();
        List<PendingAuctionImage> pendingImages = ownedPendingImages();
        when(pendingAuctionImageStore.findByMemberIdAndDraftIdAndUploadIdInForUpdate(
                MEMBER_ID, DRAFT_ID, UPLOAD_IDS
        )).thenReturn(pendingImages);
        stubMatchingMetadata(pendingImages);
        when(imageObjectKeyGenerator.generatePermanent(anyLong(), any(UUID.class), any()))
                .thenAnswer(invocation -> "auction-images/100/" + invocation.getArgument(1));

        var response = auctionCreateService.create(
                MEMBER_ID, downRequest(150_000L, 10_000L, 30L)
        );

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
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        MemberException exception = assertThrows(
                MemberException.class,
                () -> auctionCreateService.create(MEMBER_ID, upRequest(null))
        );

        assertThat(exception.getErrorCode()).isEqualTo(MEMBER_NOT_FOUND);
        verifyNoInteractions(auctionRepository, imageRepository, pendingAuctionImageStore);
    }

    @Test
    void 비활성_회원은_경매를_등록할_수_없다() {
        Member inactiveSeller = Member.builder()
                .name("판매자")
                .phoneNumber("01012345678")
                .nickname("seller")
                .email("seller@example.com")
                .password("encoded")
                .status(MemberStatus.PENDING)
                .build();
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(inactiveSeller));

        MemberException exception = assertThrows(
                MemberException.class,
                () -> auctionCreateService.create(MEMBER_ID, upRequest(null))
        );

        assertThat(exception.getErrorCode()).isEqualTo(MEMBER_NOT_ACTIVE);
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void 존재하지_않는_카테고리는_등록할_수_없다() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));

        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, withCategory(upRequest(null), "INVALID"))
        );

        assertThat(exception.getErrorCode()).isEqualTo(CATEGORY_NOT_FOUND);
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void 허용되지_않은_진행_시간은_등록할_수_없다() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));

        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, withDuration(upRequest(null), 45))
        );

        assertThat(exception.getErrorCode()).isEqualTo(INVALID_DURATION);
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void 시작가가_1000원_단위가_아니면_등록할_수_없다() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));

        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, withStartPrice(upRequest(null), 200_500L))
        );

        assertThat(exception.getErrorCode()).isEqualTo(INVALID_START_PRICE_UNIT);
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void 즉시구매가가_1000원_단위가_아니면_등록할_수_없다() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));

        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, upRequest(500_500L))
        );

        assertThat(exception.getErrorCode()).isEqualTo(INVALID_PRICE_UNIT);
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void 즉시구매가는_시작가보다_높아야_한다() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));

        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, upRequest(200_000L))
        );

        assertThat(exception.getErrorCode()).isEqualTo(INVALID_BUY_NOW_PRICE);
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void 최저가는_시작가보다_낮아야_한다() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));

        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, downRequest(200_000L, 10_000L, 30L))
        );

        assertThat(exception.getErrorCode()).isEqualTo(INVALID_MINIMUM_PRICE);
    }

    @Test
    void 최저가가_1000원_단위가_아니면_등록할_수_없다() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));

        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, downRequest(150_500L, 10_000L, 30L))
        );

        assertThat(exception.getErrorCode()).isEqualTo(INVALID_PRICE_UNIT);
    }

    @Test
    void 인하_금액이_1000원_단위가_아니면_등록할_수_없다() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));

        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, downRequest(150_000L, 10_500L, 30L))
        );

        assertThat(exception.getErrorCode()).isEqualTo(INVALID_PRICE_UNIT);
    }

    @Test
    void 하향_경매는_최저가_인하금액_인하주기가_모두_있어야_한다() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));

        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, downRequest(150_000L, null, 30L))
        );

        assertThat(exception.getErrorCode()).isEqualTo(INVALID_INPUT_VALUE);
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void 이미지가_없으면_등록할_수_없다() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));

        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(MEMBER_ID, withImageUploadIds(upRequest(null), List.of()))
        );

        assertThat(exception.getErrorCode()).isEqualTo(IMAGE_MIN_COUNT_VIOLATION);
        verifyNoInteractions(pendingAuctionImageStore);
    }

    @Test
    void 중복된_uploadId를_보내면_등록할_수_없다() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(seller));

        AuctionException exception = assertThrows(
                AuctionException.class,
                () -> auctionCreateService.create(
                        MEMBER_ID,
                        withImageUploadIds(upRequest(null), List.of(FIRST_UPLOAD_ID, FIRST_UPLOAD_ID))
                )
        );

        assertThat(exception.getErrorCode()).isEqualTo(INVALID_IMAGE_REFERENCE);
        verifyNoInteractions(pendingAuctionImageStore);
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

    private List<PendingAuctionImage> ownedPendingImages() {
        return List.of(
                PendingAuctionImage.issue(
                        MEMBER_ID, DRAFT_ID, FIRST_UPLOAD_ID, FIRST_TEMPORARY_KEY,
                        "image/jpeg", 248_392L, FIRST_CHECKSUM
                ),
                PendingAuctionImage.issue(
                        MEMBER_ID, DRAFT_ID, SECOND_UPLOAD_ID, SECOND_TEMPORARY_KEY,
                        "image/png", 128_000L, SECOND_CHECKSUM
                )
        );
    }

    private void stubMatchingMetadata(List<PendingAuctionImage> pendingImages) {
        pendingImages.forEach(image -> when(objectStorage.head(image.getObjectKey()))
                .thenReturn(Optional.of(new StoredObjectMetadata(
                        image.getContentLength(),
                        image.getContentType(),
                        image.getChecksumSha256()
                ))));
    }

    private void assertImagesSavedInOrder(Auction auction, List<String> permanentKeys) {
        ArgumentCaptor<List<Image>> imagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(imageRepository).saveAll(imagesCaptor.capture());
        List<Image> savedImages = imagesCaptor.getValue();

        assertThat(savedImages).hasSize(permanentKeys.size());
        for (int i = 0; i < permanentKeys.size(); i++) {
            assertThat(savedImages.get(i).getObjectKey()).isEqualTo(permanentKeys.get(i));
            assertThat(savedImages.get(i).getAuction()).isSameAs(auction);
        }
    }

    private AuctionCreateRequest upRequest(Long buyNowPrice) {
        return new AuctionCreateRequest(
                DRAFT_ID,
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
                UPLOAD_IDS
        );
    }

    private AuctionCreateRequest downRequest(
            Long minimumPrice,
            Long dropPrice,
            Long priceDropInterval
    ) {
        return new AuctionCreateRequest(
                DRAFT_ID,
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
                UPLOAD_IDS
        );
    }

    private AuctionCreateRequest withCategory(AuctionCreateRequest request, String category) {
        return new AuctionCreateRequest(
                request.draftId(), request.title(), request.description(), category, request.contact(),
                request.auctionType(), request.tradeType(), request.durationMinutes(), request.startPrice(),
                request.buyNowPrice(), request.minimumPrice(), request.dropPrice(), request.priceDropInterval(),
                request.imageUploadIds()
        );
    }

    private AuctionCreateRequest withDuration(AuctionCreateRequest request, int durationMinutes) {
        return new AuctionCreateRequest(
                request.draftId(), request.title(), request.description(), request.category(), request.contact(),
                request.auctionType(), request.tradeType(), durationMinutes, request.startPrice(),
                request.buyNowPrice(), request.minimumPrice(), request.dropPrice(), request.priceDropInterval(),
                request.imageUploadIds()
        );
    }

    private AuctionCreateRequest withStartPrice(AuctionCreateRequest request, long startPrice) {
        return new AuctionCreateRequest(
                request.draftId(), request.title(), request.description(), request.category(), request.contact(),
                request.auctionType(), request.tradeType(), request.durationMinutes(), startPrice,
                request.buyNowPrice(), request.minimumPrice(), request.dropPrice(), request.priceDropInterval(),
                request.imageUploadIds()
        );
    }

    private AuctionCreateRequest withImageUploadIds(
            AuctionCreateRequest request,
            List<UUID> imageUploadIds
    ) {
        return new AuctionCreateRequest(
                request.draftId(), request.title(), request.description(), request.category(), request.contact(),
                request.auctionType(), request.tradeType(), request.durationMinutes(), request.startPrice(),
                request.buyNowPrice(), request.minimumPrice(), request.dropPrice(), request.priceDropInterval(),
                imageUploadIds
        );
    }
}
