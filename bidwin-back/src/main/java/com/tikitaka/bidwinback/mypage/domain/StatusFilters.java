package com.tikitaka.bidwinback.mypage.domain;

import com.tikitaka.bidwinback.mypage.domain.exception.MyPageException;

import java.util.List;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_INPUT_VALUE;

/**
 * 백엔드 enum 이름을 그대로 필터 값으로 쓰는 탭(보증금/거래 내역)이 공유하는 파싱 로직.
 * 값이 없으면 전체, 있으면 그 enum 하나만으로 좁힌다.
 */
public final class StatusFilters {

    private StatusFilters() {
    }

    public static <E extends Enum<E>> List<E> resolve(Class<E> enumType, String code) {
        if (code == null || code.isBlank()) {
            return List.of(enumType.getEnumConstants());
        }
        try {
            return List.of(Enum.valueOf(enumType, code));
        } catch (IllegalArgumentException exception) {
            throw new MyPageException(INVALID_INPUT_VALUE, "지원하지 않는 상태 필터입니다.");
        }
    }
}
