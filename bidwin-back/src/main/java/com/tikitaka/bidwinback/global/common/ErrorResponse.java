package com.tikitaka.bidwinback.global.common;

import com.tikitaka.bidwinback.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "API 오류 정보")
public record ErrorResponse(
        @Schema(description = "애플리케이션 오류 코드", example = "COMMON_400_1")
        String code,

        @Schema(description = "사용자에게 전달할 오류 메시지", example = "요청 값이 올바르지 않습니다.")
        String message,

        @Schema(description = "오류 발생 시각", example = "2026-08-16T14:30:00")
        LocalDateTime timestamp
) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                LocalDateTime.now()
        );
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(
                errorCode.getCode(),
                message,
                LocalDateTime.now()
        );
    }
}
