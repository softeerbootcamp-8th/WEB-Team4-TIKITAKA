package com.tikitaka.bidwinback.auction.domain.exception;

import com.tikitaka.bidwinback.global.exception.BusinessException;
import com.tikitaka.bidwinback.global.exception.ErrorCode;

public class DepositException extends BusinessException {

    public DepositException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DepositException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
