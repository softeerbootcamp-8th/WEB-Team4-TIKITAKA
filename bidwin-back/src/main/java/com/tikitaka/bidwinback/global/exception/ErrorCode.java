package com.tikitaka.bidwinback.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(
            HttpStatus.BAD_REQUEST,
            "COMMON_400_1",
            "요청 값이 올바르지 않습니다."
    ),
    METHOD_NOT_ALLOWED(
            HttpStatus.METHOD_NOT_ALLOWED,
            "COMMON_405_1",
            "지원하지 않는 HTTP 메서드입니다."
    ),
    ENTITY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "COMMON_404_1",
            "요청한 리소스를 찾을 수 없습니다."
    ),
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "COMMON_500_1",
            "서버 내부 오류가 발생했습니다."
    ),

    // Member / authentication
    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "MEMBER_401_1",
            "이메일 또는 비밀번호가 일치하지 않습니다."
    ),
    UNAUTHENTICATED(
            HttpStatus.UNAUTHORIZED,
            "MEMBER_401_2",
            "로그인 세션이 없거나 만료되었습니다."
    ),
    AUTHENTICATION_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "MEMBER_503_1",
            "인증 처리를 일시적으로 완료할 수 없습니다. 잠시 후 다시 시도해주세요."
    ),
    INVALID_PASSWORD_FORMAT(
            HttpStatus.BAD_REQUEST,
            "MEMBER_400_1",
            "비밀번호는 특수문자를 1개 이상 포함해야 합니다."
    ),
    TERMS_NOT_AGREED(
            HttpStatus.BAD_REQUEST,
            "MEMBER_400_2",
            "이용약관 및 개인정보 처리방침에 동의해야 가입할 수 있습니다."
    ),
    PASSWORD_CONFIRMATION_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "MEMBER_400_3",
            "새 비밀번호와 확인 비밀번호가 일치하지 않습니다."
    ),
    INVALID_PASSWORD_RESET_TOKEN(
            HttpStatus.BAD_REQUEST,
            "MEMBER_400_4",
            "유효하지 않은 비밀번호 재설정 토큰입니다."
    ),
    EXPIRED_PASSWORD_RESET_TOKEN(
            HttpStatus.GONE,
            "MEMBER_410_1",
            "비밀번호 재설정 토큰이 만료되었습니다."
    ),
    INVALID_EMAIL_VERIFICATION_TOKEN(
            HttpStatus.BAD_REQUEST,
            "MEMBER_400_5",
            "유효하지 않은 이메일 인증 토큰입니다."
    ),
    EXPIRED_EMAIL_VERIFICATION_TOKEN(
            HttpStatus.GONE,
            "MEMBER_410_2",
            "이메일 인증 토큰이 만료되었습니다."
    ),
    DUPLICATE_EMAIL(
            HttpStatus.CONFLICT,
            "MEMBER_409_1",
            "이미 가입된 이메일입니다."
    ),
    DUPLICATE_NICKNAME(
            HttpStatus.CONFLICT,
            "MEMBER_409_2",
            "이미 사용 중인 닉네임입니다."
    ),
    EMAIL_VERIFICATION_PENDING(
            HttpStatus.CONFLICT,
            "MEMBER_409_3",
            "이미 가입 신청된 이메일입니다. 이메일 인증을 완료해주세요."
    ),
    MEMBER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "MEMBER_404_1",
            "존재하지 않는 회원입니다."
    ),
    FORBIDDEN_ACCESS(
            HttpStatus.FORBIDDEN,
            "MEMBER_403_1",
            "해당 리소스에 접근할 권한이 없습니다."
    ),

    // Auction
    AUCTION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "AUCTION_404_1",
            "존재하지 않는 경매입니다."
    ),
    CATEGORY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "AUCTION_404_2",
            "존재하지 않는 카테고리입니다."
    ),
    IMAGE_MIN_COUNT_VIOLATION(
            HttpStatus.BAD_REQUEST,
            "AUCTION_400_1",
            "상품 이미지는 최소 1장 이상 등록해야 합니다."
    ),
    INVALID_START_PRICE_UNIT(
            HttpStatus.BAD_REQUEST,
            "AUCTION_400_2",
            "시작가는 최소 1,000원 단위로 설정해야 합니다."
    ),
    INVALID_BID_UNIT(
            HttpStatus.BAD_REQUEST,
            "AUCTION_400_3",
            "입찰 호가 단위가 올바르지 않습니다."
    ),
    INVALID_BUY_NOW_PRICE(
            HttpStatus.BAD_REQUEST,
            "AUCTION_400_4",
            "즉시구매가는 시작가보다 높아야 합니다."
    ),
    INVALID_MINIMUM_PRICE(
            HttpStatus.BAD_REQUEST,
            "AUCTION_400_5",
            "최저가는 시작가보다 낮아야 합니다."
    ),
    INVALID_DURATION(
            HttpStatus.BAD_REQUEST,
            "AUCTION_400_6",
            "경매 마감 시간은 30분/1시간/3시간/6시간 중에서 선택해야 합니다."
    ),
    LOCATION_REQUIRED_FOR_DIRECT_TRADE(
            HttpStatus.BAD_REQUEST,
            "AUCTION_400_7",
            "직거래를 선택한 경우 거래 희망 위치를 입력해야 합니다."
    ),
    AUCTION_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "AUCTION_403_1",
            "본인의 경매가 아닙니다."
    ),
    AUCTION_NOT_ONGOING(
            HttpStatus.CONFLICT,
            "AUCTION_409_1",
            "진행 중인 경매가 아닙니다."
    ),
    AUCTION_ALREADY_ENDED(
            HttpStatus.CONFLICT,
            "AUCTION_409_2",
            "이미 종료된 경매입니다."
    ),
    AUCTION_ALREADY_TRADED(
            HttpStatus.CONFLICT,
            "AUCTION_409_3",
            "이미 낙찰 처리된 경매입니다."
    ),
    AUCTION_MODIFICATION_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "AUCTION_409_4",
            "등록된 경매는 수정하거나 삭제할 수 없습니다."
    ),
    INVALID_SORT(
            HttpStatus.BAD_REQUEST,
            "AUCTION_400_8",
            "지원하지 않는 정렬 기준입니다."
    ),

    // Upload
    UNSUPPORTED_IMAGE_TYPE(
            HttpStatus.BAD_REQUEST,
            "UPLOAD_400_1",
            "지원하지 않는 이미지 형식입니다."
    ),

    // Bid
    BID_PRICE_TOO_LOW(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "BID_422_1",
            "입찰가는 현재가보다 호가 단위 이상 높아야 합니다."
    ),
    SELF_BID_NOT_ALLOWED(
            HttpStatus.FORBIDDEN,
            "BID_403_1",
            "본인이 등록한 경매에는 입찰할 수 없습니다."
    ),
    INSUFFICIENT_DEPOSIT(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "BID_422_2",
            "보증금이 부족합니다."
    ),
    BUY_NOW_PRICE_NOT_SET(
            HttpStatus.BAD_REQUEST,
            "BID_400_1",
            "즉시구매가가 설정되지 않은 경매입니다."
    ),
    BUY_NOW_PRICE_CHANGED(
            HttpStatus.CONFLICT,
            "BID_409_1",
            "즉시구매가가 변경되어 처리할 수 없습니다. 최신 가격을 다시 확인해주세요."
    ),
    CONCURRENT_TRADE_CONFLICT(
            HttpStatus.CONFLICT,
            "BID_409_2",
            "다른 사용자가 먼저 구매를 확정했습니다."
    ),

    // Trade / settlement
    TRADE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "TRADE_404_1",
            "존재하지 않는 거래입니다."
    ),
    TRADE_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "TRADE_403_1",
            "본인의 거래가 아닙니다."
    ),
    TRADE_ALREADY_CONFIRMED(
            HttpStatus.CONFLICT,
            "TRADE_409_1",
            "이미 확인 처리된 거래입니다."
    ),
    INVALID_TRADE_STATUS_TRANSITION(
            HttpStatus.CONFLICT,
            "TRADE_409_2",
            "현재 거래 상태에서는 요청한 상태로 변경할 수 없습니다."
    ),
    REFUND_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "TRADE_409_3",
            "환불/취소가 불가능한 거래 상태입니다."
    ),
    DELIVERY_ADDRESS_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "TRADE_400_1",
            "택배 거래는 배송지 입력이 필요합니다."
    ),
    SETTLEMENT_NOT_AVAILABLE(
            HttpStatus.CONFLICT,
            "TRADE_409_4",
            "정산 가능한 상태의 거래가 아닙니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
