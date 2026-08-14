package com.tikitaka.bidwinback.auction.presentation.dto.request;

import com.tikitaka.bidwinback.auction.domain.AuctionPricePolicy;
import com.tikitaka.bidwinback.auction.domain.enums.AuctionType;
import com.tikitaka.bidwinback.auction.domain.enums.TradeType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record AuctionCreateRequest(
        // 이 draftId 세션에서 업로드된 이미지만 첨부하도록, images 소유권 확인 시 함께 검증한다.
        @NotNull(message = "draftId는 필수입니다.")
        UUID draftId,

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 30, message = "제목은 30자 이하로 입력해주세요.")
        String title,

        @NotBlank(message = "상품 설명은 필수입니다.")
        String description,

        // HOUSEHOLD/FOOD/FURNITURE 여부는 AuctionCategory.from()에서 검증한다.
        @NotBlank(message = "카테고리는 필수입니다.")
        String category,

        // 오픈채팅방 링크 등으로 연락받는 판매자도 있어, 휴대폰 번호 또는 http(s) 링크
        // 형식을 모두 허용한다(한글 등 의미 없는 문자열만 막는다).
        @NotBlank(message = "연락처는 필수입니다.")
        @Size(max = 100, message = "연락처는 100자 이하로 입력해주세요.")
        @Pattern(
                regexp = "^(01[016789]\\d{7,8}|https?://\\S+)$",
                message = "연락처는 하이픈 없는 휴대폰 번호 또는 http(s)로 시작하는 링크로 입력해야 합니다."
        )
        String contact,

        @NotNull(message = "경매 방식은 필수입니다.")
        AuctionType auctionType,

        @NotNull(message = "거래 방식은 필수입니다.")
        TradeType tradeType,

        // 30/60/180/360(분)만 허용되며, 실제 검증은 서비스에서 수행한다.
        @NotNull(message = "경매 진행 시간은 필수입니다.")
        Integer durationMinutes,

        // 1,000원 단위 여부는 서비스에서 검증한다. 상한은 AuctionPricePolicy가 입찰가·즉시구매가에
        // 적용하는 배타적 상한(1000억원 미만)과 맞춰, 등록만 되고 아무도 살 수 없는 가격을 막는다.
        @Min(value = 1_000, message = "시작가는 1,000원 이상이어야 합니다.")
        @Max(value = AuctionPricePolicy.MAX_PRICE_EXCLUSIVE - 1_000L, message = "시작가는 1,000억 원 미만으로 입력해주세요.")
        long startPrice,

        // 상향 경매에서만 사용하는 선택 필드.
        @Min(value = 1_000, message = "즉시구매가는 1,000원 이상이어야 합니다.")
        @Max(value = AuctionPricePolicy.MAX_PRICE_EXCLUSIVE - 1_000L, message = "즉시구매가는 1,000억 원 미만으로 입력해주세요.")
        Long buyNowPrice,

        // 아래 세 필드는 하향 경매 전용이며, 서비스에서 필수 여부를 검증한다.
        @Min(value = 1_000, message = "최저가는 1,000원 이상이어야 합니다.")
        Long minimumPrice,

        @Min(value = 1_000, message = "인하 금액은 1,000원 이상이어야 합니다.")
        Long dropPrice,

        // 1/3/5/10(분)만 허용되며, 실제 검증은 서비스에서 수행한다.
        Long priceDropInterval,

        // presign 응답의 uploadId 목록. 순서가 곧 노출 순서(첫 장이 대표 이미지)다.
        List<@NotNull UUID> imageUploadIds
) {
}
