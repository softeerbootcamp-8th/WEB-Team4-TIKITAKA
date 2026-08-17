package com.tikitaka.bidwinback.global.common;

import com.tikitaka.bidwinback.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 API 응답")
public record ApiResponse<T>(
        @Schema(description = "요청 성공 여부", example = "true")
        boolean success,

        @Schema(description = "성공 시 응답 데이터. 데이터가 없으면 null")
        T data,

        @Schema(description = "실패 시 오류 정보. 성공하면 null")
        ErrorResponse error
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> successWithoutData() {
        return new ApiResponse<>(true, null, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, null, ErrorResponse.of(errorCode));
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, null, ErrorResponse.of(errorCode, message));
    }
}
