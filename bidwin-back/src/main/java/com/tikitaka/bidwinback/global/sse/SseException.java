package com.tikitaka.bidwinback.global.sse;

import com.tikitaka.bidwinback.global.exception.BusinessException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;

public class SseException extends BusinessException {

    public SseException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SseException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
