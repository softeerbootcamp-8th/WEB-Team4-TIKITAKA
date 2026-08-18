package com.tikitaka.bidwinback.upload.domain.exception;

import com.tikitaka.bidwinback.global.exception.BusinessException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;

public class UploadException extends BusinessException {

    public UploadException(ErrorCode errorCode) {
        super(errorCode);
    }

    public UploadException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
