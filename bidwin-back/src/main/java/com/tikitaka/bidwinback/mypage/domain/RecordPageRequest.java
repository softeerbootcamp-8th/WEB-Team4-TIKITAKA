package com.tikitaka.bidwinback.mypage.domain;

import com.tikitaka.bidwinback.mypage.domain.enums.RecordSort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 프론트가 1-index로 페이지 번호를 보내므로(경매 목록 API와 동일한 규칙), 여기서만
 * Pageable의 0-index로 변환한다. 정렬 기준 컬럼은 실제 존재하는 컬럼이라(계산값이 아님)
 * Pageable의 Sort 자동 매핑을 그대로 쓸 수 있다.
 */
public final class RecordPageRequest {

    private static final int FIRST_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;

    private RecordPageRequest() {
    }

    public static Pageable of(int page, int size, String sortField, RecordSort sort) {
        int safePage = Math.max(FIRST_PAGE, page) - FIRST_PAGE;
        int safeSize = size > 0 ? size : DEFAULT_SIZE;
        return PageRequest.of(safePage, safeSize, Sort.by(sort.direction(), sortField));
    }
}
