package com.tikitaka.bidwinback.mypage.domain.enums;

import com.tikitaka.bidwinback.mypage.domain.exception.MyPageException;
import org.springframework.data.domain.Sort;

import java.util.Arrays;

import static com.tikitaka.bidwinback.global.exception.ErrorCode.INVALID_INPUT_VALUE;

/**
 * 마이페이지 내역 탭 공통 정렬. 탭마다 기준 시각 컬럼(입찰/구매/등록/변경 시각 등)은 다르지만
 * "최신순이냐 오래된순이냐"라는 방향만 결정하면 되므로 탭 전체가 이 enum 하나를 공유한다.
 */
public enum RecordSort {
    LATEST("latest", Sort.Direction.DESC),
    OLDEST("oldest", Sort.Direction.ASC);

    private static final RecordSort DEFAULT = LATEST;

    private final String wireValue;
    private final Sort.Direction direction;

    RecordSort(String wireValue, Sort.Direction direction) {
        this.wireValue = wireValue;
        this.direction = direction;
    }

    public static RecordSort from(String code) {
        if (code == null || code.isBlank()) {
            return DEFAULT;
        }
        return Arrays.stream(values())
                .filter(sort -> sort.wireValue.equals(code))
                .findFirst()
                .orElseThrow(() -> new MyPageException(INVALID_INPUT_VALUE, "지원하지 않는 정렬 기준입니다."));
    }

    public Sort.Direction direction() {
        return direction;
    }
}
