package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.application.live.AuctionCreated;
import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.Image;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionDuration;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.enums.PriceDropInterval;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.ImageRepository;
import com.tikitaka.bidwinback.auction.presentation.dto.request.AuctionCreateRequest;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionCreateResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.IMAGE_MIN_COUNT_VIOLATION;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_BUY_NOW_PRICE;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_IMAGE_REFERENCE;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_INPUT_VALUE;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_MINIMUM_PRICE;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_PRICE_UNIT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_START_PRICE_UNIT;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.MEMBER_NOT_ACTIVE;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.MEMBER_NOT_FOUND;
import static com.tikitaka.bidwinback.global.exception.ErrorCode.PRICE_DROP_INTERVAL_EXCEEDS_DURATION;

@Service
@RequiredArgsConstructor
public class AuctionCreateService {

    private static final long PRICE_UNIT = 1_000L;
    private static final int MAX_IMAGE_COUNT = 10;

    private final MemberRepository memberRepository;
    private final AuctionRepository auctionRepository;
    private final ImageRepository imageRepository;
    private final PendingAuctionImageStore pendingAuctionImageStore;
    private final AuctionImageObjectKeyGenerator imageObjectKeyGenerator;
    private final ObjectStorage objectStorage;
    private final AuctionImageStorageCleanup imageStorageCleanup;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AuctionCreateResponse create(Long memberId, AuctionCreateRequest request) {
        Member seller = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MEMBER_NOT_FOUND));
        validateSellerActive(seller);
        AuctionCategory category = AuctionCategory.from(request.category());
        AuctionDuration duration = AuctionDuration.from(request.durationMinutes());

        validateStartPriceUnit(request.startPrice());
        validatePriceRelations(request, duration);
        List<UUID> uploadIds = validateImages(request.imageUploadIds());
        List<PendingAuctionImage> pendingImages = requireOwnedImageReservations(
                memberId,
                request.draftId(),
                uploadIds
        );
        validateUploadedObjects(pendingImages);

        // 하락 경매 최종가 계산 기준과 동일하게, 마감 시각도 애플리케이션 서버 시각이 아닌 DB 시각을 기준으로 계산한다.
        // DB 호출 전에 모든 검증을 끝내, 어차피 실패할 요청 때문에 불필요한 조회가 나가지 않게 한다.
        LocalDateTime endedAt = auctionRepository.currentDatabaseTime()
                .plusMinutes(duration.getMinutes());

        Auction auction = buildAuction(seller, category, endedAt, request);
        auctionRepository.save(auction);

        // 하향 경매는 BidPriceCache를 쓰지 않으므로(즉시구매만 존재) 상향 경매만 캐시를 초기화한다.
        if (auction instanceof UpAuction) {
            eventPublisher.publishEvent(new AuctionCreated(auction.getId(), auction.getStartPrice(), endedAt));
        }

        attachImages(auction, pendingImages);

        return AuctionCreateResponse.from(auction);
    }

    private void validateSellerActive(Member seller) {
        if (seller.getStatus() != MemberStatus.ACTIVE) {
            throw new MemberException(MEMBER_NOT_ACTIVE, "활성 상태의 회원만 경매를 등록할 수 있습니다.");
        }
    }

    private void validatePriceRelations(AuctionCreateRequest request, AuctionDuration duration) {
        if (request.auctionType() == AuctionType.UP) {
            Long buyNowPrice = request.buyNowPrice();
            if (buyNowPrice != null) {
                validatePriceUnit(buyNowPrice, "즉시구매가");
                if (buyNowPrice <= request.startPrice()) {
                    throw new AuctionException(INVALID_BUY_NOW_PRICE);
                }
            }
            return;
        }

        if (request.minimumPrice() == null || request.dropPrice() == null || request.priceDropInterval() == null) {
            throw new AuctionException(
                    INVALID_INPUT_VALUE,
                    "하향 경매는 최저가·인하 금액·인하 주기를 모두 입력해야 합니다."
            );
        }
        validatePriceUnit(request.minimumPrice(), "최저가");
        validatePriceUnit(request.dropPrice(), "인하 금액");
        if (request.minimumPrice() >= request.startPrice()) {
            throw new AuctionException(INVALID_MINIMUM_PRICE);
        }
        PriceDropInterval priceDropInterval = PriceDropInterval.from(request.priceDropInterval());
        if (priceDropInterval.getMinutes() > duration.getMinutes()) {
            throw new AuctionException(PRICE_DROP_INTERVAL_EXCEEDS_DURATION);
        }
    }

    private void validatePriceUnit(long price, String fieldName) {
        if (price % PRICE_UNIT != 0) {
            throw new AuctionException(INVALID_PRICE_UNIT, fieldName + "는 1,000원 단위로 입력해주세요.");
        }
    }

    private Auction buildAuction(
            Member seller,
            AuctionCategory category,
            LocalDateTime endedAt,
            AuctionCreateRequest request
    ) {
        if (request.auctionType() == AuctionType.UP) {
            return UpAuction.builder()
                    .seller(seller)
                    .title(request.title())
                    .description(request.description())
                    .category(category)
                    .startPrice(request.startPrice())
                    .endedAt(endedAt)
                    .tradeType(request.tradeType())
                    .contact(request.contact())
                    .buyNowPrice(request.buyNowPrice())
                    .build();
        }

        return DownAuction.builder()
                .seller(seller)
                .title(request.title())
                .description(request.description())
                .category(category)
                .startPrice(request.startPrice())
                .endedAt(endedAt)
                .tradeType(request.tradeType())
                .contact(request.contact())
                .minimumPrice(request.minimumPrice())
                .dropPrice(request.dropPrice())
                .priceDropInterval(request.priceDropInterval())
                .build();
    }

    private void validateStartPriceUnit(long startPrice) {
        if (startPrice % PRICE_UNIT != 0) {
            throw new AuctionException(INVALID_START_PRICE_UNIT);
        }
    }

    private List<UUID> validateImages(List<UUID> uploadIds) {
        if (uploadIds == null || uploadIds.isEmpty()) {
            throw new AuctionException(IMAGE_MIN_COUNT_VIOLATION);
        }
        if (uploadIds.size() > MAX_IMAGE_COUNT) {
            throw new AuctionException(
                    INVALID_INPUT_VALUE,
                    "상품 이미지는 최대 10장까지 등록할 수 있습니다."
            );
        }
        if (uploadIds.stream().distinct().count() != uploadIds.size()) {
            throw new AuctionException(INVALID_IMAGE_REFERENCE);
        }
        return uploadIds;
    }

    private List<PendingAuctionImage> requireOwnedImageReservations(
            long memberId,
            UUID draftId,
            List<UUID> uploadIds
    ) {
        // 같은 예약을 동시 등록 요청이 중복 소비하거나 만료 정리 작업이 먼저 삭제하지 못하도록 행을 잠근다.
        List<PendingAuctionImage> pendingImages = pendingAuctionImageStore
                .findByMemberIdAndDraftIdAndUploadIdInForUpdate(
                        memberId,
                        draftId,
                        uploadIds
                );
        Map<UUID, PendingAuctionImage> imagesByUploadId = pendingImages.stream()
                .collect(Collectors.toMap(
                        PendingAuctionImage::getUploadId,
                        image -> image
                ));
        if (imagesByUploadId.size() != uploadIds.size()) {
            throw new AuctionException(INVALID_IMAGE_REFERENCE);
        }
        return uploadIds.stream()
                .map(imagesByUploadId::get)
                .toList();
    }

    private void validateUploadedObjects(List<PendingAuctionImage> pendingImages) {
        // Presigned URL 발급 조건만 신뢰하지 않고, 등록 시점의 실제 S3 객체를 예약 정보와 다시 대조한다.
        for (PendingAuctionImage pendingImage : pendingImages) {
            StoredObjectMetadata metadata = objectStorage
                    .head(pendingImage.getObjectKey())
                    .orElseThrow(() -> new AuctionException(INVALID_IMAGE_REFERENCE));

            if (metadata.contentLength() != pendingImage.getContentLength()
                    || !pendingImage.getContentType().equalsIgnoreCase(metadata.contentType())
                    || !pendingImage.getChecksumSha256().equals(metadata.checksumSha256())) {
                throw new AuctionException(
                        INVALID_IMAGE_REFERENCE,
                        "업로드된 이미지 정보가 발급 조건과 일치하지 않습니다."
                );
            }
        }
    }

    private void attachImages(
            Auction auction,
            List<PendingAuctionImage> pendingImages
    ) {
        Map<UUID, String> permanentKeys = new HashMap<>();
        for (PendingAuctionImage pendingImage : pendingImages) {
            permanentKeys.put(
                    pendingImage.getUploadId(),
                    imageObjectKeyGenerator.generatePermanent(
                            auction.getId(),
                            pendingImage.getUploadId(),
                            AuctionImageFileType.fromContentType(
                                    pendingImage.getContentType()
                            )
                    )
            );
        }

        List<String> promotedObjectKeys = new ArrayList<>();
        List<String> temporaryObjectKeys = pendingImages.stream()
                .map(PendingAuctionImage::getObjectKey)
                .toList();

        // S3 작업은 DB 트랜잭션과 함께 롤백되지 않으므로 완료 상태에 따라 반대편 객체를 보상 삭제한다.
        imageStorageCleanup.register(temporaryObjectKeys, promotedObjectKeys);
        for (PendingAuctionImage pendingImage : pendingImages) {
            String permanentKey = permanentKeys.get(pendingImage.getUploadId());
            // 복사 전에 기록해야 이후 복사가 실패해도 이미 승격된 객체를 빠짐없이 정리할 수 있다.
            promotedObjectKeys.add(permanentKey);
            objectStorage.copy(pendingImage.getObjectKey(), permanentKey);
        }

        // pendingImages 순서(=사용자가 정렬한 노출 순서) 그대로 Image 행을 만든다.
        List<Image> images = pendingImages.stream()
                .map(pendingImage -> Image.builder()
                        .auction(auction)
                        .objectKey(permanentKeys.get(pendingImage.getUploadId()))
                        .build())
                .toList();
        imageRepository.saveAll(images);

        pendingAuctionImageStore.deleteByObjectKeyIn(temporaryObjectKeys);
    }
}
