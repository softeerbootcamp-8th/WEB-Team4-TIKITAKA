package com.tikitaka.bidwinback.global.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Spring의 Page를 그대로 응답하면 pageable, sort 등 내부 구현 정보가 API 계약에 노출된다.
 * 프론트가 쓰는 1-index 페이지 번호로 변환해 필요한 필드만 내려준다.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int totalPages,
        long totalCount
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber() + 1,
                page.getTotalPages(),
                page.getTotalElements()
        );
    }

    public static <T, R> PageResponse<R> from(Page<T> page, List<R> mappedItems) {
        return new PageResponse<>(
                mappedItems,
                page.getNumber() + 1,
                page.getTotalPages(),
                page.getTotalElements()
        );
    }
}
