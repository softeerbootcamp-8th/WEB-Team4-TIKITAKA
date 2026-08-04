package com.tikitaka.bidwinback.auction.application;

import com.tikitaka.bidwinback.auction.domain.entity.Auction;
import com.tikitaka.bidwinback.auction.domain.entity.DownAuction;
import com.tikitaka.bidwinback.auction.domain.entity.Image;
import com.tikitaka.bidwinback.auction.domain.entity.UpAuction;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionCategory;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionDuration;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.exception.AuctionException;
import com.tikitaka.bidwinback.auction.domain.repository.AuctionRepository;
import com.tikitaka.bidwinback.auction.domain.repository.ImageRepository;
import com.tikitaka.bidwinback.auction.presentation.dto.request.AuctionCreateRequest;
import com.tikitaka.bidwinback.auction.presentation.dto.response.AuctionCreateResponse;
import com.tikitaka.bidwinback.member.domain.entity.Member;
import com.tikitaka.bidwinback.member.domain.enums.MemberStatus;
import com.tikitaka.bidwinback.member.domain.exception.MemberException;
import com.tikitaka.bidwinback.member.domain.repository.MemberRepository;
import com.tikitaka.bidwinback.upload.domain.PendingAuctionImageStore;
import com.tikitaka.bidwinback.upload.domain.entity.PendingAuctionImage;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
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

@Service
@RequiredArgsConstructor
public class AuctionCreateService {

    private static final long PRICE_UNIT = 1_000L;

    private final MemberRepository memberRepository;
    private final AuctionRepository auctionRepository;
    private final ImageRepository imageRepository;
    private final PendingAuctionImageStore pendingAuctionImageStore;

    @Transactional
    public AuctionCreateResponse create(Long memberId, AuctionCreateRequest request) {
        Member seller = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MEMBER_NOT_FOUND));
        validateSellerActive(seller);
        AuctionCategory category = AuctionCategory.from(request.category());
        AuctionDuration duration = AuctionDuration.from(request.durationMinutes());

        validateStartPriceUnit(request.startPrice());
        validatePriceRelations(request);
        List<String> objectKeys = validateImages(request.images());

        // 하락 경매 최종가 계산 기준과 동일하게, 마감 시각도 애플리케이션 서버 시각이 아닌 DB 시각을 기준으로 계산한다.
        // DB 호출 전에 모든 검증을 끝내, 어차피 실패할 요청 때문에 불필요한 조회가 나가지 않게 한다.
        LocalDateTime endedAt = auctionRepository.currentDatabaseTime()
                .plusMinutes(duration.getMinutes());

        Auction auction = buildAuction(seller, category, endedAt, request);
        auctionRepository.save(auction);

        attachImages(auction, memberId, request.draftId(), objectKeys);

        return AuctionCreateResponse.from(auction);
    }

    private void validateSellerActive(Member seller) {
        if (seller.getStatus() != MemberStatus.ACTIVE) {
            throw new MemberException(MEMBER_NOT_ACTIVE, "활성 상태의 회원만 경매를 등록할 수 있습니다.");
        }
    }

    private void validatePriceRelations(AuctionCreateRequest request) {
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

    private List<String> validateImages(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            throw new AuctionException(IMAGE_MIN_COUNT_VIOLATION);
        }
        return objectKeys;
    }

    private void attachImages(Auction auction, Long memberId, UUID draftId, List<String> objectKeys) {
        List<PendingAuctionImage> pendingImages =
                pendingAuctionImageStore.findByMemberIdAndDraftIdAndObjectKeyIn(memberId, draftId, objectKeys);

        Set<String> ownedObjectKeys = pendingImages.stream()
                .map(PendingAuctionImage::getObjectKey)
                .collect(Collectors.toSet());
        if (!ownedObjectKeys.containsAll(objectKeys)) {
            throw new AuctionException(INVALID_IMAGE_REFERENCE);
        }

        // objectKeys 순서(=사용자가 정렬한 노출 순서) 그대로 Image 행을 만든다.
        List<Image> images = objectKeys.stream()
                .map(objectKey -> Image.builder()
                        .auction(auction)
                        .objectKey(objectKey)
                        .build())
                .toList();
        try {
            // Image.objectKey의 unique 제약이 마지막 방어선이다. 등록 요청이 중복 도착해
            // 두 경매가 같은 objectKey를 동시에 가져가려 하면, 늦게 커밋되는 쪽이 여기서 걸린다.
            imageRepository.saveAll(images);
        } catch (DataIntegrityViolationException exception) {
            throw new AuctionException(INVALID_IMAGE_REFERENCE, "이미 다른 경매에 사용된 이미지입니다.");
        }

        pendingAuctionImageStore.deleteByObjectKeyIn(objectKeys);
    }
}
